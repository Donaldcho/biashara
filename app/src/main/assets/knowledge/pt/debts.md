# Como Gerir Dívidas de Clientes
feature_id: debts
language: pt

O BiasharaAI permite registar dívidas quando um cliente leva produtos sem pagar no momento. Pode acompanhar quanto cada cliente deve e marcar dívidas como pagas quando receber o pagamento.

## Como se Cria uma Dívida

Uma dívida é criada automaticamente quando, durante uma venda no POS, seleciona **Dívida** como método de pagamento. O valor total da venda fica registado como dívida do cliente.

Também pode registar uma dívida manualmente.

## Registar uma Dívida Manualmente

1. No menu inferior, toque em **Dívidas** ou aceda a partir de **Clientes > [nome do cliente] > Adicionar Dívida**.
2. Toque no botão **+**.
3. Preencha os campos:
   - **Cliente**: Selecione o cliente na lista ou adicione um novo.
   - **Valor**: Insira o montante em dívida.
   - **Data de Vencimento** (opcional): Defina uma data limite para pagamento.
   - **Descrição** (opcional): Descreva o motivo da dívida (ex: "Compra de arroz e óleo").
4. Toque em **Guardar**.

## Ver Todas as Dívidas

1. No menu inferior, toque em **Dívidas**.
2. A lista mostra todas as dívidas em aberto com:
   - Nome do cliente.
   - Valor total em dívida.
   - Data de criação.
   - Data de vencimento (se definida).
3. As dívidas em atraso aparecem destacadas a vermelho.

## Filtrar Dívidas

Use os filtros no topo do ecrã para ver:
- **Todas as dívidas**: Todas as dívidas em aberto.
- **Em atraso**: Dívidas que passaram da data de vencimento.
- **Por cliente**: Dívidas de um cliente específico.

## Marcar uma Dívida como Paga

Quando o cliente pagar:

1. Na lista de dívidas, toque na dívida em questão.
2. Toque em **Registar Pagamento**.
3. Insira o valor pago pelo cliente.
   - Se pagar o valor total, a dívida ficará marcada como **Paga** e será arquivada.
   - Se pagar apenas parte, o saldo restante continua registado como dívida em aberto.
4. Selecione o método de pagamento: Dinheiro ou Mobile Money.
5. Toque em **Confirmar Pagamento**.

## Pagamento Parcial

O aplicativo suporta pagamentos parciais. Por exemplo:
- Dívida total: 500 unidades.
- Cliente paga 200 unidades.
- Saldo restante: 300 unidades — continua como dívida ativa.

## Enviar Lembrete ao Cliente

Para lembretes de pagamento:
1. Abra a dívida do cliente.
2. Toque em **Enviar Lembrete**.
3. Escolha o canal: **SMS** ou **WhatsApp**.
4. O aplicativo cria uma mensagem automática com o valor e a data de vencimento.
5. Confirme o envio.

## Ver Histórico de Pagamentos

1. Aceda a **Clientes > [nome do cliente]**.
2. Toque em **Histórico de Pagamentos**.
3. Verá todas as dívidas pagas e a data de cada pagamento.

## Relatório de Dívidas

Para ver um resumo geral:
1. Vá a **Relatórios**.
2. Selecione **Dívidas em Aberto**.
3. Verá o total em dívida e uma lista por cliente.

## Dicas

- Defina sempre uma data de vencimento. Ajuda a lembrar quando cobrar.
- Envie lembretes regulares aos clientes com dívidas em atraso.
- Não deixe acumular muitas dívidas sem acompanhamento — pode afetar o fluxo de caixa do negócio.
