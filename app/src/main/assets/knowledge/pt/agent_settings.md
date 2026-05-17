# Como Configurar o Comportamento do Agente IA
feature_id: agent_settings
language: pt

As definições do agente permitem personalizar como o assistente IA funciona no BiasharaAI. Pode ativar ou desativar habilidades específicas, definir horários de análise e ajustar a sensibilidade dos alertas.

## Aceder às Definições do Agente

**Opção 1:**
1. No menu inferior, toque em **Agente**.
2. No canto superior direito do feed, toque no ícone de **engrenagem**.

**Opção 2:**
1. Vá a **Definições** no menu principal.
2. Selecione **Agente IA**.

## Secção: Habilidades (Skills)

O agente usa um conjunto de "habilidades" para analisar diferentes aspetos do negócio. Cada habilidade pode ser ativada ou desativada individualmente.

### Habilidades Disponíveis

- **Sentinela de Fluxo de Caixa**: Monitoriza entradas e saídas de dinheiro. Alerta quando o fluxo de caixa está em risco.
- **Monitor de Stock**: Verifica os níveis de stock e alerta quando um produto está a ficar esgotado.
- **Análise de Dívidas**: Acompanha dívidas de clientes e alerta sobre pagamentos em atraso.
- **Detetor de Perdas**: Identifica produtos a ser vendidos abaixo do custo.
- **Tendências de Vendas**: Analisa padrões de vendas ao longo do tempo.
- **Fidelização de Clientes**: Identifica clientes regulares e clientes que pararam de comprar.
- **Previsão de Stock**: Prevê quando os produtos vão esgotar com base no histórico de vendas.

### Como Ativar ou Desativar uma Habilidade

1. Na lista de habilidades, toque no interruptor ao lado do nome da habilidade.
2. Verde significa **ativa**. Cinzento significa **inativa**.
3. As alterações ficam imediatas.

## Secção: Horários e Frequência

Configure quando o agente analisa os dados:

- **Análise em tempo real**: O agente analisa cada transação assim que é registada. Pode gerar mais notificações.
- **Análise diária**: O agente analisa os dados uma vez por dia. Menos interrupções.
- **Hora da análise diária**: Defina a hora preferida para a análise (ex: 07:00 da manhã).
- **Análise semanal**: Resumo enviado uma vez por semana (ex: segunda-feira de manhã).

## Secção: Alertas e Notificações

Configure os tipos de notificações que recebe:

- **Notificações push**: Ative para receber alertas no painel de notificações do dispositivo.
- **Nível de urgência mínimo**: Defina o nível mínimo de alerta que quer receber (Baixo, Médio, Alto, Crítico).
- **Som de alerta**: Ative ou desative o som nas notificações do agente.

## Secção: Limiares de Alerta

Ajuste quando o agente deve disparar certos alertas:

- **Stock mínimo**: Defina o número de unidades abaixo do qual o alerta de stock baixo é ativado. O valor padrão é 5 unidades.
- **Dias sem pagamento**: Número de dias de dívida em aberto antes de o agente alertar. O padrão é 14 dias.
- **Queda de vendas (%)**: Percentagem de queda nas vendas que ativa o alerta de tendência negativa. O padrão é 20%.

## Repor as Definições Padrão

Se quiser voltar às definições originais:
1. No fundo do ecrã de definições do agente, toque em **Repor para Valores Padrão**.
2. Confirme na janela de diálogo.

## Dicas

- Se receber demasiadas notificações, aumente o nível de urgência mínimo para **Médio** ou **Alto**.
- Ative sempre a habilidade **Detetor de Perdas** — é uma das mais importantes para a saúde do negócio.
- A **Previsão de Stock** é útil para negócios com produtos sazonais ou de alta rotatividade.
- Se o agente estiver a consumir muita bateria, mude para **Análise diária** em vez de tempo real.
