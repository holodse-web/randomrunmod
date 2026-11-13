$ErrorActionPreference = 'Stop'
$jdk = Join-Path $PSScriptRoot '.tools\jdk25\jdk-25.0.4.1+1'
$oldJavaHome = $env:JAVA_HOME
try {
    if (Test-Path -LiteralPath (Join-Path $jdk 'bin\java.exe')) { $env:JAVA_HOME = $jdk }
    Push-Location $PSScriptRoot
    try {
        & .\gradlew.bat clean build --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE" }
        $out = Join-Path $PSScriptRoot 'release'
        New-Item -ItemType Directory -Force -Path $out | Out-Null
        # the mod jar (not the -sources jar); no obfuscation
        $built = Get-ChildItem -Path (Join-Path $PSScriptRoot 'build\libs') -Filter '*.jar' | Where-Object { $_.Name -notlike '*-sources.jar' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        Copy-Item -Force $built.FullName $out
        $jar = Get-Item (Join-Path $out $built.Name)
        $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $jar.FullName).Hash.ToLowerInvariant()
        $hashFile = Join-Path $PSScriptRoot 'server\src\mod-hashes.json'
        $known = @()
        if (Test-Path -LiteralPath $hashFile) { $known = @([regex]::Matches((Get-Content -Raw -LiteralPath $hashFile), '[0-9a-f]{64}') | ForEach-Object { $_.Value }) }
        # Keep the fingerprints of older builds too (up to 10), so players on the previous version can still log in.
        $list = @(@($hash) + @($known | Where-Object { $_ -ne $hash }) | Select-Object -First 10)
        Set-Content -Encoding ascii -LiteralPath $hashFile -Value ('[' + ((@($list) | ForEach-Object { '"' + $_ + '"' }) -join ',') + ']')
        Write-Host "Mod jar is in $out."
        Write-Host "Mod fingerprint $hash saved to server\src\mod-hashes.json. Run deploy-server.bat so the server accepts this build."
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $oldJavaHome
}
