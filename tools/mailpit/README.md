# Mailpit Local Mail Sandbox

LapPick is configured to send mail to Mailpit by default.

## Start

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\mailpit\start-mailpit.ps1
```

## Stop

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\mailpit\stop-mailpit.ps1
```

## URLs

- Web UI: `http://127.0.0.1:8025`
- SMTP: `127.0.0.1:1025`

## Notes

- The start script downloads the latest Mailpit Windows binary from the official GitHub release page if needed.
- Emails stay local and are not delivered to real inboxes.
