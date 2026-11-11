$ErrorActionPreference = 'Stop'
$jdk = Join-Path $PSScriptRoot '.tools\jdk25\jdk-25.0.4.1+1'
$oldJavaHome = $env:JAVA_HOME
try {
    if (Test-Path -LiteralPath (Join-Path $jdk 'bin\java.exe')) { $env:JAVA_HOME = $jdk }
    Push-Location $PSScriptRoot
    try {
        & .\gradlew.bat build obfuscatedJar --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE" }
        $out = Join-Path $PSScriptRoot 'release'
        New-Item -ItemType Directory -Force -Path $out | Out-Null
        Copy-Item -Force (Join-Path $PSScriptRoot 'build\obfuscated\release\*.jar') $out
        Copy-Item -Force (Join-Path $PSScriptRoot 'build\obfuscated\mapping.txt') (Join-Path $out 'mapping.txt')
        $jar = Get-ChildItem -Path $out -Filter '*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $jar.FullName).Hash.ToLowerInvariant()
        $hashFile = Join-Path $PSScriptRoot 'server\src\mod-hashes.json'
        $known = @()
        if (Test-Path -LiteralPath $hashFile) { $known = @([regex]::Matches((Get-Content -Raw -LiteralPath $hashFile), '[0-9a-f]{64}') | ForEach-Object { $_.Value }) }
        $list = @(@($hash) + @($known | Where-Object { $_ -ne $hash }) | Select-Object -First 3)
        Set-Content -Encoding ascii -LiteralPath $hashFile -Value ('[' + ((@($list) | ForEach-Object { '"' + $_ + '"' }) -join ',') + ']')
        Write-Host "Obfuscated jar and mapping.txt are in $out. Do not publish mapping.txt."
        Write-Host "Mod fingerprint $hash saved to server\src\mod-hashes.json. Run deploy-server.bat so the server accepts this build."
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $oldJavaHome
}
