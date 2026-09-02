$files = Get-ChildItem -Path src -Recurse -File -Filter *.java
$violations = [System.Collections.Generic.List[string]]::new()

foreach ($file in $files) {
    $content = [System.IO.File]::ReadAllText($file.FullName)
    if (-not $content.EndsWith("`n")) {
        $violations.Add("$($file.FullName): missing final newline")
    }

    $lineNumber = 0
    foreach ($line in [System.IO.File]::ReadLines($file.FullName)) {
        $lineNumber++
        if ($line -match "\s+$") {
            $violations.Add("$($file.FullName):$lineNumber trailing whitespace")
        }
    }
}

if ($violations.Count -gt 0) {
    throw "Formatting convention violations:`n$($violations -join "`n")"
}
