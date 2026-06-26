# OLIVE — 8080/3000 포트 점유 프로세스 + cloudflared 종료.
function Free-Port($port) {
    $pids = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -Expand OwningProcess -Unique
    foreach ($procId in $pids) {
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        Write-Host "killed PID $procId (port $port)"
    }
}
Free-Port 8080
Free-Port 3000
Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Write-Host "done."
