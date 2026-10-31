# Brings the stack up, drives the API end to end, and exits non-zero on any mismatch.
param(
  [string]$BaseUrl = 'http://localhost:8080',
  [int]$TimeoutSeconds = 240,
  [switch]$SkipUp
)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

function Fail($message) {
  Write-Host "SMOKE FAILED: $message"
  exit 1
}

function Post($path, $body, $headers = @{}) {
  Invoke-RestMethod -Method Post -Uri "$BaseUrl$path" -Headers $headers `
    -ContentType 'application/json' -Body ($body | ConvertTo-Json)
}

function Wait-Until($description, [scriptblock]$condition) {
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    try {
      if (& $condition) { return }
      $lastError = 'condition was false'
    } catch {
      $lastError = $_.Exception.Message
    }
    Start-Sleep -Seconds 2
  }
  Fail "timed out waiting for $description ($lastError)"
}

if (-not $SkipUp) {
  if (-not (Test-Path .env)) { Copy-Item .env.example .env }
  docker compose --profile app up -d --build
  if ($LASTEXITCODE -ne 0) { Fail 'docker compose up failed' }
}

Wait-Until 'readiness' {
  (Invoke-RestMethod "$BaseUrl/actuator/health/readiness").status -eq 'UP'
}
Write-Host 'Service is ready'

$suffix = [guid]::NewGuid().ToString('N').Substring(0, 8)
$gameId = "smoke-$suffix"

$alice = Post '/api/v1/players' @{ username = "alice_$suffix" }
$bob = Post '/api/v1/players' @{ username = "bob_$suffix" }
$aliceSession = Post '/api/v1/sessions' @{ playerId = $alice.id; gameId = $gameId }
$bobSession = Post '/api/v1/sessions' @{ playerId = $bob.id; gameId = $gameId }
if ($aliceSession.status -ne 'ACTIVE') { Fail "session status was $($aliceSession.status)" }

$first = Post '/api/v1/scores' @{ playerId = $alice.id; sessionId = $aliceSession.id; points = 100 } @{ 'Idempotency-Key' = "a1-$suffix" }
$replay = Post '/api/v1/scores' @{ playerId = $alice.id; sessionId = $aliceSession.id; points = 999 } @{ 'Idempotency-Key' = "a1-$suffix" }
if ($replay.id -ne $first.id -or $replay.points -ne 100) { Fail 'idempotent replay returned a different score' }
Post '/api/v1/scores' @{ playerId = $bob.id; sessionId = $bobSession.id; points = 250 } @{ 'Idempotency-Key' = "b1-$suffix" } | Out-Null
Post '/api/v1/scores' @{ playerId = $alice.id; sessionId = $aliceSession.id; points = 40 } @{ 'Idempotency-Key' = "a2-$suffix" } | Out-Null

# Invoke-RestMethod returns a JSON array as one object; the pipeline unrolls it into rows.
function Get-Top {
  @(Invoke-RestMethod "$BaseUrl/api/v1/leaderboards/$gameId/top?limit=10" | ForEach-Object { $_ })
}

Wait-Until 'scores to reach the leaderboard' { (Get-Top).Count -eq 2 }

$top = Get-Top
if ($top[0].username -ne "bob_$suffix" -or $top[0].score -ne 250 -or $top[0].rank -ne 1) { Fail "unexpected first place: $($top[0] | ConvertTo-Json -Compress)" }
if ($top[1].username -ne "alice_$suffix" -or $top[1].score -ne 100 -or $top[1].rank -ne 2) { Fail "unexpected second place: $($top[1] | ConvertTo-Json -Compress)" }

$standing = Invoke-RestMethod "$BaseUrl/api/v1/leaderboards/$gameId/players/$($alice.id)"
if ($standing.rank -ne 2 -or $standing.score -ne 100) { Fail "unexpected standing: $($standing | ConvertTo-Json -Compress)" }

$rebuilt = Post "/api/v1/admin/leaderboards/$gameId/rebuild" @{}
if ($rebuilt.rebuilt -ne 2) { Fail "rebuild returned $($rebuilt.rebuilt)" }

$ended = Invoke-RestMethod -Method Post "$BaseUrl/api/v1/sessions/$($aliceSession.id)/end"
if ($ended.status -ne 'ENDED') { Fail "end returned $($ended.status)" }

Write-Host 'SMOKE PASSED'
exit 0
