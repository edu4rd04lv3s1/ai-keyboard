# Performance workflow

Use este fluxo quando estiver ajustando latência de digitação:

1. Instale `optimizedDebug`, não `debug`, para validar sensação real.
2. Digite pelo menos 100 teclas no app de destino.
3. Abra o app de configuração e confira `P50`, `P95` e `P99` na seção `Desempenho`.
4. Compare sempre no mesmo aparelho, mesmo app de destino e mesma configuração de sugestões/vibração.
5. Se houver regressão, capture um trace com Perfetto antes de tentar novas mudanças.

Comandos úteis:

```bash
./gradlew testDebugUnitTest assembleOptimizedDebug
```

O APK otimizado fica em:

```text
app/build/outputs/apk/optimizedDebug/app-optimizedDebug.apk
```
