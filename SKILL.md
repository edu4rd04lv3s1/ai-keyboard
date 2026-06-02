---
name: whatsapp-teste-aprovacao-dudu-alves
description: Teste imediato: enviar mensagem para Dudu Alves para aprovar permissões do WhatsApp
---

TAREFA DE TESTE (pré-aprovação): enviar uma mensagem curta para o contato "Dudu Alves" no WhatsApp Desktop do Mac. O objetivo é aprovar permissões do WhatsApp para que as próximas tarefas agendadas rodem sem pausar.

PASSOS:

1) Carregue as ferramentas de computer-use via ToolSearch com query "computer-use" e max_results 30.

2) Chame `mcp__computer-use__request_access` solicitando acesso ao aplicativo usando o bundle ID `net.whatsapp.WhatsApp` (NÃO use "WhatsApp" como display name — o sistema não encontra por causa de um caractere invisível no nome). Passe também `clipboardWrite: true`.

3) Use `mcp__computer-use__open_application` com `net.whatsapp.WhatsApp` para trazer o app para frente. Aguarde ~2s para o app carregar.

4) Tire um screenshot com `mcp__computer-use__screenshot` para ver o estado atual do WhatsApp.

5) Clique no campo de busca de conversas (no topo da barra lateral esquerda, label "Pesquisar") e digite "Dudu Alves".

6) Clique na conversa do "Dudu Alves 🤍 (Você)" para abri-la — é o chat "Mensagens para mim" (auto-chat). Se houver mais de um resultado, escolha o que aparece primeiro sob a seção "Conversas".

7) Clique no campo de digitação de mensagem na parte inferior.

8) Use `mcp__computer-use__write_clipboard` para colocar no clipboard o seguinte texto:

"Teste 1 — pode ignorar. (pré-aprovação de permissões)"

Depois cole no campo com Cmd+V.

9) Confira via screenshot que a mensagem está correta no campo de digitação ANTES de enviar.

10) Aperte Enter (key "Return") UMA ÚNICA VEZ para enviar.

11) Tire um screenshot final para confirmar que a mensagem foi enviada (deve aparecer na conversa com o horário e duplo check).

12) Se algo der errado (WhatsApp não abre, pede QR code, contato não encontrado, etc.), PARE imediatamente e reporte. NÃO envie para o contato errado.

Reporte ao final: sucesso ou falha, com screenshot da conversa.
