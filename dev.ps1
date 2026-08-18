# Local build helper. Needs JDK 25 on PATH or JAVA_HOME.
param(
	[Parameter(ValueFromRemainingArguments = $true)]
	[string[]]$GradleArgs = @("build")
)

$ErrorActionPreference = "Stop"

if (-not $env:JAVA_HOME) {
	$jdkDir = Join-Path $env:USERPROFILE ".jdks"
	if (Test-Path $jdkDir) {
		$found = Get-ChildItem $jdkDir -Directory -ErrorAction SilentlyContinue |
			Where-Object { $_.Name -like "jdk-25*" } |
			Select-Object -First 1
		if ($found) {
			$env:JAVA_HOME = $found.FullName
		}
	}
}

$gradlew = Join-Path $PSScriptRoot "gradlew.bat"
& $gradlew @GradleArgs
exit $LASTEXITCODE
