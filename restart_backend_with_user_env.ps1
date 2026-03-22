$ErrorActionPreference = 'Stop'

$workingDir = 'E:\test\backend-java'
$logPath = Join-Path $workingDir 'backend-run.log'

$accessKeyId = [Environment]::GetEnvironmentVariable('ALIYUN_OSS_ACCESS_KEY_ID', 'User')
$accessKeySecret = [Environment]::GetEnvironmentVariable('ALIYUN_OSS_ACCESS_KEY_SECRET', 'User')

if ([string]::IsNullOrWhiteSpace($accessKeyId) -or [string]::IsNullOrWhiteSpace($accessKeySecret)) {
    'Missing OSS credentials in user environment.' | Set-Content -Path $logPath -Encoding UTF8
    exit 1
}

Get-CimInstance Win32_Process |
    Where-Object {
        $_.Name -eq 'java.exe' -and
        $_.CommandLine -match 'com\.campusfit\.CampusFitApplication' -and
        $_.CommandLine -match 'E:\\test\\backend-java'
    } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force
    }

$env:ALIYUN_OSS_ACCESS_KEY_ID = $accessKeyId
$env:ALIYUN_OSS_ACCESS_KEY_SECRET = $accessKeySecret

Set-Location $workingDir
'Starting backend with user environment...' | Set-Content -Path $logPath -Encoding UTF8
mvn spring-boot:run *>> $logPath
