from pathlib import Path
import runpy

application = Path("app/src/main/java/app/arbor/chat/ArborApplication.kt")
text = application.read_text()
current = '''    val pythonSandbox = PythonSandbox(application)
    val ubuntuRuntime = UbuntuRuntime(application, pythonSandbox)
    val linuxEnvironmentArchives = LinuxEnvironmentArchiveStore(application, pythonSandbox, ubuntuRuntime)
    val archiveManager = ArborArchiveManager(application, database, linuxEnvironmentArchives)
    val scopedCloudFolder = ScopedCloudFolderStore(application)
    val googleDriveAppData = GoogleDriveAppDataClient(application)
'''
temporary = '''    val pythonSandbox = PythonSandbox(application)
    val linuxEnvironmentArchives = LinuxEnvironmentArchiveStore(application, pythonSandbox)
    val archiveManager = ArborArchiveManager(application, database, linuxEnvironmentArchives)
    val scopedCloudFolder = ScopedCloudFolderStore(application)
    val googleDriveAppData = GoogleDriveAppDataClient(application)
    val ubuntuRuntime = UbuntuRuntime(application, pythonSandbox)
'''
if current in text:
    application.write_text(text.replace(current, temporary, 1))
elif temporary not in text and "val appSettingsArchives = AppSettingsArchiveStore" not in text:
    raise RuntimeError("Current AppContainer transfer-service layout was not recognized")

runpy.run_path("scripts/apply_setup_restore_settings_0_20_25.py", run_name="__main__")

text = application.read_text()
patched = '''    val pythonSandbox = PythonSandbox(application)
    val linuxEnvironmentArchives = LinuxEnvironmentArchiveStore(application, pythonSandbox)
    val appSettingsArchives = AppSettingsArchiveStore(application, appPreferences, database)
    val archiveManager = ArborArchiveManager(application, database, linuxEnvironmentArchives, appSettingsArchives)
    val scopedCloudFolder = ScopedCloudFolderStore(application)
    val googleDriveAppData = GoogleDriveAppDataClient(application)
    val ubuntuRuntime = UbuntuRuntime(application, pythonSandbox)
'''
final = '''    val pythonSandbox = PythonSandbox(application)
    val ubuntuRuntime = UbuntuRuntime(application, pythonSandbox)
    val linuxEnvironmentArchives = LinuxEnvironmentArchiveStore(application, pythonSandbox, ubuntuRuntime)
    val appSettingsArchives = AppSettingsArchiveStore(application, appPreferences, database)
    val archiveManager = ArborArchiveManager(application, database, linuxEnvironmentArchives, appSettingsArchives)
    val scopedCloudFolder = ScopedCloudFolderStore(application)
    val googleDriveAppData = GoogleDriveAppDataClient(application)
'''
if patched in text:
    application.write_text(text.replace(patched, final, 1))
elif final not in text:
    raise RuntimeError("Patched AppContainer transfer-service layout was not recognized")
