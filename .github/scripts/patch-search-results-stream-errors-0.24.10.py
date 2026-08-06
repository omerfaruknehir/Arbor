import runpy

runpy.run_path(
    ".github/scripts/patch-search-results-stream-errors-core-0.24.10.py",
    run_name="__main__",
)
runpy.run_path(
    ".github/scripts/patch-qwen-cloud-0.24.10.py",
    run_name="__main__",
)
