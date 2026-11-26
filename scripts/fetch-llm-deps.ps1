$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot\.."
$modelsDir = Join-Path $root "apps\android\app\src\main\assets\models"
$cppDir = Join-Path $root "apps\android\app\src\main\cpp"

New-Item -ItemType Directory -Force -Path $modelsDir, $cppDir | Out-Null

$ggufPath = Join-Path $modelsDir "smollm2-135m-instruct-q4_k_m.gguf"
if (-not (Test-Path $ggufPath)) {
    Write-Host "Downloading SmolLM2-135M-Instruct-Q4_K_M.gguf (~105 MB)..."
    curl.exe -L --fail --retry 3 -o $ggufPath "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf"
}

$llamaDir = Join-Path $cppDir "llama.cpp"
if (-not (Test-Path (Join-Path $llamaDir "CMakeLists.txt"))) {
    Write-Host "Cloning llama.cpp..."
    git clone --depth 1 https://github.com/ggml-org/llama.cpp.git $llamaDir
}

Write-Host "Done."
