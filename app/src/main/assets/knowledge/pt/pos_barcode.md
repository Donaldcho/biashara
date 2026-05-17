# Como Usar o Leitor de Código de Barras no POS
feature_id: pos_barcode
language: pt

O BiasharaAI permite escanear códigos de barras diretamente no Ponto de Venda para adicionar produtos ao carrinho de forma rápida e sem erros.

## Requisitos

- O dispositivo deve ter uma câmara traseira funcional.
- O produto deve ter um código de barras registado no inventário.
- A permissão de câmara deve estar concedida ao aplicativo.

## Como Ativar o Scanner no POS

1. Abra o ecrã de **POS** (Ponto de Venda) tocando no menu inferior.
2. No topo do ecrã, toque no ícone de **câmara** ou **código de barras**.
3. A câmara será ativada e aparecerá uma linha de guia no ecrã.

## Escanear um Produto

1. Com a câmara ativa, aponte o dispositivo para o código de barras do produto.
2. Mantenha o dispositivo estável e a uma distância de 10 a 20 cm do código.
3. O aplicativo detecta o código automaticamente — não precisa de pressionar nenhum botão.
4. O produto correspondente será adicionado ao carrinho imediatamente.
5. Ouvirá um som de confirmação e verá o produto no carrinho.

## Escanear Vários Produtos

1. Após escanear o primeiro produto, o scanner permanece ativo.
2. Aponte para o próximo produto e ele será adicionado ao carrinho.
3. Continue até adicionar todos os produtos.
4. Para parar de escanear, toque no botão **Fechar Scanner** ou pressione o botão de voltar.

## Produto Não Reconhecido

Se o código de barras não corresponder a nenhum produto no inventário:

1. Aparecerá uma mensagem: "Produto não encontrado".
2. Poderá optar por:
   - **Pesquisar manualmente** o produto pelo nome.
   - **Adicionar o produto** ao inventário agora, associando o código de barras.
3. Toque em **Adicionar ao Inventário** para registar um novo produto com aquele código de barras.

## Registar um Código de Barras num Produto Existente

1. Vá a **Inventário**.
2. Toque no produto para abrir os detalhes.
3. Toque em **Editar**.
4. No campo **Código de Barras**, toque no ícone de câmara.
5. Escaneie o código de barras do produto.
6. Toque em **Guardar**.

## Dicas para um Bom Scan

- Certifique-se de que há boa iluminação. Evite luz direta e sombras sobre o código.
- Segure o dispositivo firmemente para evitar tremidos.
- Se o código estiver danificado ou amassado, tente pesquisar o produto pelo nome.
- Códigos de barras 1D (linhas) e QR codes 2D são suportados.
- Alguns produtos têm mais de um código de barras (embalagem individual e por caixa). Registe ambos no inventário se necessário.

## Permissão de Câmara

Se o scanner não funcionar, verifique as permissões:
1. No dispositivo, vá a **Definições > Aplicativos > BiasharaAI > Permissões**.
2. Ative a permissão de **Câmara**.
3. Volte ao aplicativo e tente novamente.

O scanner de código de barras torna o processo de venda muito mais rápido, especialmente em lojas com muitos produtos.
