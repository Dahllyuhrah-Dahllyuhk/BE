$file = "C:\matuabom\BE\src\main\java\org\dallyeo\matuabom\calendar\service\GoogleCalendarService.java"
$bytes = [System.IO.File]::ReadAllBytes($file)
if ($bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
    $newBytes = $bytes[3..($bytes.Length-1)]
    [System.IO.File]::WriteAllBytes($file, $newBytes)
    Write-Host "BOM removed from GoogleCalendarService.java"
} else {
    Write-Host "No BOM found in GoogleCalendarService.java"
}
