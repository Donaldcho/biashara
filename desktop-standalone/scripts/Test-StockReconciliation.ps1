param(
    [int]$UiPort = 8766,
    [int]$PhonePort = 8866,
    [switch]$VerifyRetryPersistence
)

$ErrorActionPreference = "Stop"
$uiBase = "http://127.0.0.1:$UiPort"
$phoneBase = "http://127.0.0.1:$PhonePort"

function Invoke-JsonPost {
    param([string]$Uri, [hashtable]$Body, [hashtable]$Headers = @{})
    Invoke-RestMethod -Method Post -Uri $Uri -Headers $Headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 12 -Compress)
}

function Get-Stock {
    param([string]$ProductName)
    $state = Invoke-RestMethod -Method Get -Uri "$uiBase/api/state"
    return [int](($state.products | Where-Object name -eq $ProductName | Select-Object -First 1).stock)
}

function Assert-Stock {
    param([int]$Expected, [string]$Step)
    $actual = Get-Stock "Sync Test Product"
    if ($actual -ne $Expected) {
        throw "$Step expected stock $Expected but found $actual."
    }
}

$discovery = Invoke-RestMethod -Method Get -Uri "$phoneBase/api/phone/discovery"
$pair = Invoke-JsonPost -Uri "$phoneBase/api/phone/pair" -Body @{ token = $discovery.token; deviceName = "Sync smoke phone" }
$headers = @{ "X-Biashara-Session" = $pair.sessionKey }

if ($VerifyRetryPersistence) {
    Assert-Stock 12 "Persisted stock before retry"
    $persistedRetry = @{
        sessionKey = $pair.sessionKey
        deviceName = "Sync smoke phone"
        operationId = "reconcile-stock-mutation-2"
        includeImages = $false
        stockChanges = @(@{
            mobileProductId = "101"
            name = "Sync Test Product"
            barcode = "SYNC-TEST-101"
            stockBaseKnown = $true
            stockBase = 7
            stock = 13
            mutationId = "stock-mutation-2"
        })
    }
    Invoke-JsonPost -Uri "$phoneBase/api/phone/reconcile" -Headers $headers -Body $persistedRetry | Out-Null
    Assert-Stock 12 "Persisted mutation retry"
    [pscustomobject]@{ persistedStock = 12; repeatedMutationIgnored = $true } | ConvertTo-Json -Compress
    exit 0
}

$catalog = @{
    sessionKey = $pair.sessionKey
    deviceName = "Sync smoke phone"
    operationId = "catalog-sync-test-product-v1"
    mobileProductId = "101"
    name = "Sync Test Product"
    description = "Stock reconciliation smoke test"
    barcode = "SYNC-TEST-101"
    category = "Test"
    stock = 10
    priceCents = 1000
    costCents = 600
}
Invoke-JsonPost -Uri "$phoneBase/api/phone/product-sync" -Headers $headers -Body $catalog | Out-Null
Assert-Stock 10 "Initial mobile catalog"

$product = (Invoke-RestMethod -Method Get -Uri "$uiBase/api/state").products | Where-Object name -eq "Sync Test Product" | Select-Object -First 1
Invoke-JsonPost -Uri "$uiBase/api/sale" -Body @{
    paymentMethod = "Cash"
    paidCents = 2000
    lines = @(@{ kind = "PRODUCT"; itemId = $product.id; quantity = 2 })
} | Out-Null
Assert-Stock 8 "Desktop sale"

Invoke-JsonPost -Uri "$phoneBase/api/phone/product-sync" -Headers $headers -Body $catalog | Out-Null
Assert-Stock 8 "Stale phone catalog retry"

$mobileSale = @{
    sessionKey = $pair.sessionKey
    deviceName = "Sync smoke phone"
    operationId = "mobile-transaction-sync-smoke-1"
    mobileTransactionId = "mobile-sale-1"
    receiptNumber = "MOB-SMOKE-1"
    createdAtMillis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    type = "INCOME"
    description = "Mobile sale"
    paymentMethod = "CASH"
    subtotalCents = 1000
    productSubtotalCents = 1000
    serviceSubtotalCents = 0
    taxCents = 0
    totalCents = 1000
    paidCents = 1000
    balanceCents = 0
    lines = @(@{
        kind = "PRODUCT"
        mobileProductId = "101"
        name = "Sync Test Product"
        barcode = "SYNC-TEST-101"
        quantity = 1
        unitCents = 1000
        lineTotalCents = 1000
    })
}
$saleAck = Invoke-JsonPost -Uri "$phoneBase/api/phone/transaction-sync" -Headers $headers -Body $mobileSale
if (-not $saleAck.stockApplied) { throw "Mobile sale stock movement was not acknowledged." }
Assert-Stock 7 "Mobile sale"

Invoke-JsonPost -Uri "$uiBase/api/sale" -Body @{
    paymentMethod = "Cash"
    paidCents = 1000
    lines = @(@{ kind = "PRODUCT"; itemId = $product.id; quantity = 1 })
} | Out-Null
Assert-Stock 6 "Concurrent desktop sale"

$reconcile = @{
    sessionKey = $pair.sessionKey
    deviceName = "Sync smoke phone"
    operationId = "reconcile-stock-mutation-1"
    includeImages = $false
    stockChanges = @(@{
        mobileProductId = "101"
        name = "Sync Test Product"
        barcode = "SYNC-TEST-101"
        stockBaseKnown = $true
        stockBase = 7
        stock = 12
        mutationId = "stock-mutation-1"
    })
}
Invoke-JsonPost -Uri "$phoneBase/api/phone/reconcile" -Headers $headers -Body $reconcile | Out-Null
Assert-Stock 11 "Concurrent phone restock"

Invoke-JsonPost -Uri "$phoneBase/api/phone/reconcile" -Headers $headers -Body $reconcile | Out-Null
Assert-Stock 11 "Idempotent retry"

$reconcile.stockChanges[0].stock = 13
$reconcile.stockChanges[0].mutationId = "stock-mutation-2"
$reconcile.operationId = "reconcile-stock-mutation-2"
Invoke-JsonPost -Uri "$phoneBase/api/phone/reconcile" -Headers $headers -Body $reconcile | Out-Null
Assert-Stock 12 "Superseding retry"

$csv = Invoke-WebRequest -UseBasicParsing -Method Get -Uri "$uiBase/api/whatsapp/catalog.csv"
if ($csv.Headers["Content-Type"] -notlike "text/csv*") { throw "Catalog export did not return CSV." }
if ($csv.Content -notmatch "id,title,description,availability,condition,price,link,image_link,brand") {
    throw "Catalog CSV headers are incomplete."
}

[pscustomobject]@{
    initialStock = 10
    afterDesktopSale = 8
    afterMobileSale = 7
    afterConcurrentMerge = 11
    afterRetry = 11
    afterSupersedingChange = 12
    csvExport = "passed"
} | ConvertTo-Json -Compress
