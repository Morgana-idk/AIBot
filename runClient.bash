#!/bin/bash

clear
# 1. Caminho do log (ajusta se precisar)
LOG_FILE="$HOME/Documents/projetos/mods/AIBot/run/logs/latest.log"

# 2. Espera o arquivo existir (se não existir ainda)
while [ ! -f "$LOG_FILE" ]; do
    echo "[AIBot] Esperando o arquivo $LOG_FILE existir..."
    sleep 1
done

# 3. Roda o tail -F em background DE VERDADE (com nohup e redirecionamento)
nohup tail -F "$LOG_FILE" > /dev/null 2>&1 &

# 4. Guarda o PID do tail pra matar depois (opcional)
TAIL_PID=$!

# 5. Roda o Gradle (Minecraft)
echo "[AIBot] Iniciando o Minecraft..."
./gradlew runClient

# 6. (Opcional) Mata o tail quando o Minecraft fechar
kill $TAIL_PID 2>/dev/null