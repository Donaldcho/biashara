# Como Configurar uma Impressora Térmica Bluetooth
feature_id: printer_setup
language: pt

O BiasharaAI suporta impressoras térmicas Bluetooth para imprimir recibos das vendas. Siga estes passos para ligar a sua impressora ao aplicativo.

## Impressoras Compatíveis

O aplicativo é compatível com a maioria das impressoras térmicas Bluetooth de 58mm e 80mm que usam o protocolo ESC/POS. Exemplos populares:
- Xprinter XP-P300
- GOOJPRT PT-210
- MUNBYN ITPP941
- Outras impressoras térmicas portáteis de marcas similares.

## Passo 1 — Preparar a Impressora

1. Certifique-se de que a impressora tem papel instalado corretamente.
2. Carregue a impressora ou certifique-se de que as pilhas têm carga suficiente.
3. Ligue a impressora pressionando o botão de poder.
4. Aguarde até o indicador LED ficar estável (geralmente azul ou verde).

## Passo 2 — Ativar o Bluetooth no Dispositivo Android

1. Vá às **Definições do Android** (fora do aplicativo BiasharaAI).
2. Toque em **Bluetooth**.
3. Ative o Bluetooth se estiver desativado.
4. O dispositivo ficará visível para outros dispositivos.

## Passo 3 — Emparelhar a Impressora

1. Nas definições de Bluetooth do Android, toque em **Procurar Dispositivos** ou **Adicionar Dispositivo**.
2. Aguarde que a impressora apareça na lista (ex: "BT Printer", "Xprinter", "BlueTooth Printer" ou nome semelhante).
3. Toque no nome da impressora para iniciar o emparelhamento.
4. Se for pedido um PIN, introduza **0000** ou **1234** (os PINs mais comuns para impressoras térmicas).
5. O emparelhamento fica concluído quando aparecer **Emparelhado** junto ao nome da impressora.

## Passo 4 — Configurar no BiasharaAI

1. Abra o BiasharaAI.
2. Vá a **Definições > Impressora**.
3. Toque em **Procurar Impressoras Bluetooth**.
4. A sua impressora deverá aparecer na lista de dispositivos emparelhados.
5. Toque no nome da impressora para a selecionar.
6. Toque em **Ligar**.
7. Toque em **Imprimir Página de Teste** para confirmar que está a funcionar.

## Tamanho do Papel

Selecione o tamanho correto do papel da sua impressora:

1. Em **Definições > Impressora**, toque em **Tamanho do Papel**.
2. Selecione **58mm** ou **80mm** conforme o modelo da sua impressora.
3. Toque em **Guardar**.

## Imprimir um Recibo Após uma Venda

1. Após concluir uma venda no POS, aparecerá um ecrã de confirmação.
2. Toque em **Imprimir Recibo**.
3. O recibo será enviado para a impressora automaticamente.
4. Se a impressora não estiver ligada, o aplicativo tentará reconectar automaticamente.

## Personalizar o Recibo

Em **Definições > Impressora > Formato do Recibo**, pode configurar:
- **Cabeçalho**: Nome do negócio, morada, telefone, logótipo (texto).
- **Rodapé**: Mensagem de agradecimento, condições de devolução, etc.
- **Tamanho da fonte**: Normal ou grande.
- **Incluir IVA no recibo**: Sim ou Não.

## Problemas Comuns

- **Impressora não aparece na lista**: Certifique-se de que o Bluetooth está ativo e a impressora está em modo de emparelhamento (consulte o manual da impressora).
- **Impressão com caracteres estranhos**: Verifique se o tamanho do papel está correto nas definições.
- **Conexão perdida frequentemente**: Mantenha a impressora próxima do dispositivo (máximo 10 metros sem obstáculos).
- **Papel não avança**: Verifique se o papel está instalado no sentido correto (o lado térmico deve ficar para fora).

## Dicas

- Carregue sempre a impressora no final do dia para estar pronta no dia seguinte.
- Mantenha papel extra em stock para não ficar sem recibos em horas de pico.
- Se usar várias impressoras, pode configurar uma como **impressora padrão**.
