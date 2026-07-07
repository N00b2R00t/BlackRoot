package com.example

import android.content.Context
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

sealed class AppMode {
    object Terminal : AppMode()
    object IDE : AppMode()
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

    // Load initial file content
    LaunchedEffect(selectedFileRelativePath) {
        val content = workspaceManager.readFile(selectedFileRelativePath)
        fileContentState = TextFieldValue(content)
    }

    fun refreshWorkspace() {
        filesList = workspaceManager.listFiles()
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
        log("   -[ BLACKROOT DECRYPTOR CONSOLE v4.15-RELEASE ]-", LogType.SUCCESS)
        log("   + -- --=[ 42 security modules - 21 auxiliary scanners ]", LogType.INFO)
        log("   + -- --=[ Default login key: blackroot                  ]", LogType.INFO)
        log("  -|------------------------------------------------------------------|-", LogType.SYSTEM)
        log("Mounting secure workspace systems... [OK]", LogType.SUCCESS)
        log("Type 'help' for instructions matrix.", LogType.SUCCESS)
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
      |   /  \ /  \   |     BLACKROOT TERMINAL CLIENT v15
      |   |  | |  |   |     User: blackroot@sandbox
      |   \__/ \__/   |     Access Level: ROOT DECRYPTOR
       \             /      Status: Sandboxed Enclave
        '-.__...__.-'       RAM Hardware: 100% OK
""".trimIndent(), LogType.SUCCESS)
            }

            "python" -> {
                if (tokens.size > 1) {
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
                            color = NeonGreen,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(NeonGreen)
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
                                .clickable { currentMode = AppMode.Terminal }
                                .background(if (currentMode is AppMode.Terminal) NeonGreen else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .testTag("toggle_terminal"),
                            color = if (currentMode is AppMode.Terminal) PitchBlack else NeonGreen,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "IDE",
                            modifier = Modifier
                                .clickable { currentMode = AppMode.IDE }
                                .background(if (currentMode is AppMode.IDE) NeonGreen else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .testTag("toggle_ide"),
                            color = if (currentMode is AppMode.IDE) PitchBlack else NeonGreen,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Telemetry Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CPU: [OK] // RAM: [OPTIMIZED]",
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
                                text = "ACCESS RESTRICTED",
                                color = BrightRed,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            )
                            
                            Text(
                                text = "BLACKROOT SHIELD SECURE LOGIN",
                                color = GhostGreen,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                            
                            if (showPasswordError) {
                                Text(
                                    text = "DECRYPTION KEY MISMATCH! ACCESS DENIED.",
                                    color = BrightRed,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                            
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it; showPasswordError = false },
                                label = { Text("Decryption Key", color = NeonGreen, fontFamily = FontFamily.Monospace) },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("cli_password_input"),
                                textStyle = TextStyle(color = NeonGreen, fontFamily = FontFamily.Monospace, fontSize = 16.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonGreen,
                                    unfocusedBorderColor = BorderGreen,
                                    focusedLabelColor = NeonGreen,
                                    unfocusedLabelColor = GhostGreen,
                                    cursorColor = NeonGreen
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (passwordInput == "blackroot") {
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
                                    border = BorderStroke(1.dp, BorderGreen)
                                ) {
                                    Text("CANCEL (IDE)", color = NeonGreen, fontFamily = FontFamily.Monospace)
                                }
                                
                                Button(
                                    onClick = {
                                        if (passwordInput == "blackroot") {
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
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                                    modifier = Modifier.weight(1f).testTag("submit_login_button"),
                                    border = BorderStroke(1.dp, NeonGreen)
                                ) {
                                    Text("DECRYPT", color = NeonGreen, fontFamily = FontFamily.Monospace)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Hint: default password is 'blackroot'",
                                color = GhostGreen,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
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

                            // Suggested Quick Controls Ribbon
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .background(DarkGrey)
                                    .border(1.dp, BorderGreen, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val suggestions = listOf("help", "ls", "git status", "git log", "python welcome.py", "clear")
                                LazyColumn(
                                    modifier = Modifier.height(32.dp),
                                    userScrollEnabled = true
                                ) {
                                    item {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            suggestions.forEach { sug ->
                                                Box(
                                                    modifier = Modifier
                                                        .background(DarkGreen, RoundedCornerShape(4.dp))
                                                        .border(1.dp, BorderGreen, RoundedCornerShape(4.dp))
                                                        .clickable { cliInput = sug }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = sug,
                                                        color = NeonGreen,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
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
                                color = NeonGreen,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
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
                                                Text(
                                                    text = wFile.name,
                                                    color = if (isSelected) NeonGreen else GhostGreen,
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

                        // Programmers Ribbon actions bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
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
                                                        val currentText = fileContentState.text
                                                        val selectionStart = fileContentState.selection.start
                                                        val newText = currentText.substring(0, selectionStart) + insertText + currentText.substring(fileContentState.selection.end)
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

                        // Actions Panel: SAVE, RUN
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                colors = ButtonDefaults.buttonColors(containerColor = BorderGreen)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Save file", tint = PitchBlack)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SAVE ENCRYPTED", color = PitchBlack, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    // Save then execute in Terminal mode
                                    workspaceManager.writeFile(selectedFileRelativePath, fileContentState.text)
                                    refreshWorkspace()
                                    currentMode = AppMode.Terminal
                                    executeCommand("python $selectedFileRelativePath")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("run_file_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Run Python script", tint = PitchBlack)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("RUN SCRIPT", color = PitchBlack, fontWeight = FontWeight.Bold)
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
        if (extension == "py") {
            // Python keyword lists
            val keywords = setOf(
                "def", "class", "import", "for", "in", "if", "else", "elif",
                "print", "return", "while", "from", "and", "or", "not", "try", "except"
            )
            
            val lines = code.split("\n")
            lines.forEachIndexed { lineIdx, line ->
                var pos = 0
                val length = line.length
                while (pos < length) {
                    val char = line[pos]
                    
                    // Comment line
                    if (char == '#') {
                        withStyle(style = SpanStyle(color = GhostGreen, fontWeight = FontWeight.Normal)) {
                            append(line.substring(pos))
                        }
                        break
                    }
                    
                    // Strings
                    if (char == '"' || char == '\'') {
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
                            withStyle(style = SpanStyle(color = TextGreen)) {
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
        } else if (extension == "xml") {
            // XML styling highlights
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
