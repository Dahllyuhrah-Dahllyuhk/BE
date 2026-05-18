$f = 'C:\matuabom\BE\src\main\java\org\dallyeo\matuabom\calendar\service\GoogleCalendarService.java'
$bytes = [System.IO.File]::ReadAllBytes($f)
$content = [System.Text.Encoding]::UTF8.GetString($bytes)
$lines = $content -split "`n"

Write-Host "Lines 724-735:"
for ($i = 723; $i -le 735; $i++) {
    Write-Host "${i}: $($lines[$i])"
}
