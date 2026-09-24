param(
    [int]$LocalPort = 15432
)

$ErrorActionPreference = 'Stop'

function Test-LocalPort {
    param([int]$Port)

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync('127.0.0.1', $Port)
        return $task.Wait(500) -and $client.Connected
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

if (Test-LocalPort -Port $LocalPort) {
    $listener = Get-NetTCPConnection -LocalPort $LocalPort -State Listen -ErrorAction Stop |
        Where-Object LocalAddress -eq '127.0.0.1' | Select-Object -First 1
    $owner = if ($listener) { Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)" }
    if (-not $owner -or $owner.Name -ne 'ssh.exe' -or
        $owner.CommandLine -notmatch 'aiagent-tunnel@124\.223\.198\.166' -or
        $owner.CommandLine -notmatch "${LocalPort}:127\.0\.0\.1:15432") {
        throw "Local port $LocalPort is occupied by another process. Stop it before starting this project."
    }
    Write-Host "Reusing pgvector SSH tunnel on 127.0.0.1:$LocalPort."
    exit 0
}

$sshPath = (Get-Command 'ssh.exe' -ErrorAction Stop).Source
$keyPath = Join-Path $env:USERPROFILE '.ssh\ai_agent_scaffold_pgvector'
if (-not (Test-Path -LiteralPath $keyPath)) {
    throw "SSH tunnel key does not exist: $keyPath"
}

$sshArguments = @(
    '-N',
    '-T',
    '-L', "127.0.0.1:${LocalPort}:127.0.0.1:15432",
    '-o', 'BatchMode=yes',
    '-o', 'IdentitiesOnly=yes',
    '-o', 'ConnectTimeout=8',
    '-o', 'ExitOnForwardFailure=yes',
    '-o', 'ServerAliveInterval=30',
    '-o', 'ServerAliveCountMax=3',
    '-o', 'StrictHostKeyChecking=accept-new',
    '-i', ('"' + $keyPath + '"'),
    '-p', '2222',
    'aiagent-tunnel@124.223.198.166'
)

$logDirectory = Join-Path $PSScriptRoot '../../data/dev'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$errorLog = Join-Path $logDirectory 'pgvector-tunnel.stderr.log'
$sshProcess = Start-Process `
    -FilePath $sshPath `
    -ArgumentList $sshArguments `
    -WindowStyle Hidden `
    -RedirectStandardError $errorLog `
    -PassThru

for ($attempt = 0; $attempt -lt 40; $attempt++) {
    if (Test-LocalPort -Port $LocalPort) {
        Write-Host "Started pgvector SSH tunnel on 127.0.0.1:$LocalPort (PID $($sshProcess.Id))."
        exit 0
    }
    if ($sshProcess.HasExited) {
        throw "SSH tunnel exited with code $($sshProcess.ExitCode). See $errorLog"
    }
    Start-Sleep -Milliseconds 250
}

if (-not $sshProcess.HasExited) {
    Stop-Process -Id $sshProcess.Id
}
throw "SSH tunnel did not open local port $LocalPort within 10 seconds."
