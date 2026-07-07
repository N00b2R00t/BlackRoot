package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

data class WorkspaceFile(
    val name: String,
    val relativePath: String,
    val file: File,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

data class GitCommit(
    val hash: String,
    val message: String,
    val author: String,
    val timestamp: Long,
    val changedFiles: List<String>
)

class WorkspaceManager(private val context: Context) {
    private val workspaceDir: File = File(context.filesDir, "workspace")
    private val gitRegistryFile: File = File(context.filesDir, "git_registry.json")
    
    // Virtual Git State
    var stagedFiles = mutableSetOf<String>()
    var commits = mutableListOf<GitCommit>()
    
    init {
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs()
            createStarterFiles()
        }
        loadGitRegistry()
    }

    private fun createStarterFiles() {
        try {
            writeFile("welcome.txt", """
============================================================
              ______  _              _     ______                 _ 
              | ___ \| |            | |    | ___ \               | |
              | |_/ /| |  __ _  ___ | | __ | |_/ / ___   ___   __| |
              | ___ \| | / _` |/ __|| |/ / |    / / _ \ / _ \ / _` |
              | |_/ /| || (_| |\__ \|   <  | |\ \| (_) | (_) | (_| |
              \____/ |_| \__,_||___/|_|\_\ \_| \_|\___/ \___/ \__,_|
============================================================
Welcome to BlackRoot OS terminal and IDE environment!
An advanced hacking-themed sandbox for Android 15.

Available quick commands:
- 'help' : Display commands matrix.
- 'python welcome.py' : Run Python interpreter.
- 'git help' : Show virtual Git controls.
- 'ls' : Inspect local workspace.

Author: BlackRoot Systems.
Encryption Status: Standard Base-256
============================================================
""".trimIndent())

            writeFile("welcome.py", """
# BlackRoot Python Interpreter Demo
# Optimized for mid-range hardware
import math

print("Initializing BlackRoot system diagnostics...")
version = "3.11.2"
codename = "HackerGreen"

print(f"System Version: {version}")
print(f"Codename: {codename}")

def check_status(is_active):
    if is_active:
        return "SECURE"
    else:
        return "VULNERABLE"

status = check_status(True)
print(f"Securty Level: {status}")

# Running a loop
print("Decrypting secure registers:")
for i in range(5):
    val = i * 7 + 12
    print(f" -> Buffer [{i}]: Decoded hex code = {hex(val)}")

print("System diagnostics complete.")
""".trimIndent())

            writeFile("config.xml", """<?xml version="1.0" encoding="UTF-8"?>
<blackroot-config>
    <terminal>
        <theme>matrix-neon</theme>
        <font-size>12sp</font-size>
        <glow-enabled>true</glow-enabled>
    </terminal>
    <ide>
        <line-numbers>true</line-numbers>
        <tab-size>4</tab-size>
        <auto-save>false</auto-save>
    </ide>
    <network>
        <bypass-dns>true</bypass-dns>
        <mock-ping>false</mock-ping>
    </network>
</blackroot-config>
""".trimIndent())
        } catch (e: Exception) {
            Log.e("WorkspaceManager", "Starter files creation failed", e)
        }
    }

    // --- File Operations ---

    fun getWorkspacePath(): String {
        return workspaceDir.absolutePath
    }

    fun listFiles(): List<WorkspaceFile> {
        val list = mutableListOf<WorkspaceFile>()
        traverse(workspaceDir, list)
        return list.sortedWith(compareBy({ !it.isDirectory }, { it.relativePath }))
    }

    private fun traverse(dir: File, list: MutableList<WorkspaceFile>) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            val relative = f.absolutePath.removePrefix(workspaceDir.absolutePath).removePrefix("/")
            if (relative.startsWith(".git") && relative != ".git_history") continue // Skip internal git folder simulations
            list.add(WorkspaceFile(
                name = f.name,
                relativePath = relative,
                file = f,
                isDirectory = f.isDirectory,
                size = if (f.isDirectory) 0 else f.length(),
                lastModified = f.lastModified()
            ))
            if (f.isDirectory) {
                traverse(f, list)
            }
        }
    }

    fun readFile(relativePath: String): String {
        val target = File(workspaceDir, relativePath)
        if (!target.exists() || target.isDirectory) return ""
        return target.readText(Charsets.UTF_8)
    }

    fun writeFile(relativePath: String, content: String) {
        val target = File(workspaceDir, relativePath)
        target.parentFile?.mkdirs()
        target.writeText(content, Charsets.UTF_8)
    }

    fun createDirectory(relativePath: String): Boolean {
        val target = File(workspaceDir, relativePath)
        return target.mkdirs()
    }

    fun deleteFile(relativePath: String): Boolean {
        val target = File(workspaceDir, relativePath)
        if (!target.exists()) return false
        val success = target.deleteRecursively()
        // Remove from staged files if deleted
        stagedFiles.remove(relativePath)
        return success
    }

    // --- Git System Simulation / Integration ---

    private fun loadGitRegistry() {
        try {
            if (gitRegistryFile.exists()) {
                val text = gitRegistryFile.readText()
                val json = JSONObject(text)
                
                // Load staged
                val stagedArray = json.optJSONArray("staged")
                if (stagedArray != null) {
                    for (i in 0 until stagedArray.length()) {
                        stagedFiles.add(stagedArray.getString(i))
                    }
                }

                // Load commits
                val commitsArray = json.optJSONArray("commits")
                if (commitsArray != null) {
                    for (i in 0 until commitsArray.length()) {
                        val cJson = commitsArray.getJSONObject(i)
                        val files = mutableListOf<String>()
                        val cFilesArray = cJson.optJSONArray("files")
                        if (cFilesArray != null) {
                            for (j in 0 until cFilesArray.length()) {
                                files.add(cFilesArray.getString(j))
                            }
                        }
                        commits.add(GitCommit(
                            hash = cJson.getString("hash"),
                            message = cJson.getString("message"),
                            author = cJson.getString("author"),
                            timestamp = cJson.getLong("timestamp"),
                            changedFiles = files
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WorkspaceManager", "Failed to load git registry", e)
        }
    }

    fun saveGitRegistry() {
        try {
            val json = JSONObject()
            
            val stagedArray = JSONArray()
            stagedFiles.forEach { stagedArray.put(it) }
            json.put("staged", stagedArray)

            val commitsArray = JSONArray()
            commits.forEach { commit ->
                val cJson = JSONObject()
                cJson.put("hash", commit.hash)
                cJson.put("message", commit.message)
                cJson.put("author", commit.author)
                cJson.put("timestamp", commit.timestamp)
                
                val cFilesArray = JSONArray()
                commit.changedFiles.forEach { cFilesArray.put(it) }
                cJson.put("files", cFilesArray)
                
                commitsArray.put(cJson)
            }
            json.put("commits", commitsArray)

            gitRegistryFile.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.e("WorkspaceManager", "Failed to save git registry", e)
        }
    }

    fun getGitStatus(): Map<String, String> {
        val statusMap = mutableMapOf<String, String>()
        val currentFiles = listFiles().filter { !it.isDirectory }
        val trackedFilesHashes = getTrackedFilesHashes()

        currentFiles.forEach { wFile ->
            val relPath = wFile.relativePath
            val currentHash = calculateHash(wFile.file)
            val baseHash = trackedFilesHashes[relPath]

            if (baseHash == null) {
                // Untracked or Staged (if newly added)
                if (stagedFiles.contains(relPath)) {
                    statusMap[relPath] = "Staged (New File)"
                } else {
                    statusMap[relPath] = "Untracked"
                }
            } else if (currentHash != baseHash) {
                // Modified
                if (stagedFiles.contains(relPath)) {
                    statusMap[relPath] = "Staged (Modified)"
                } else {
                    statusMap[relPath] = "Modified"
                }
            } else {
                // Unmodified, but check if staged just in case
                if (stagedFiles.contains(relPath)) {
                    statusMap[relPath] = "Staged"
                }
            }
        }

        // Check for Deleted tracked files
        trackedFilesHashes.keys.forEach { relPath ->
            val f = File(workspaceDir, relPath)
            if (!f.exists()) {
                if (stagedFiles.contains(relPath)) {
                    statusMap[relPath] = "Staged (Deleted)"
                } else {
                    statusMap[relPath] = "Deleted"
                }
            }
        }

        return statusMap
    }

    private fun getTrackedFilesHashes(): Map<String, String> {
        // Track the final state of files across all commits to represent the "HEAD" state
        val hashes = mutableMapOf<String, String>()
        // Simple mock tracked database: Let's assume files are tracked when committed
        // We can simulate commits by accumulating file states
        // In this mock, we can fetch file contents from the commits' history
        // Or simple: let's save committed file hashes in `.git_registry.json` under "tracked_hashes"
        // Let's read from the registry or compute them based on saved commits
        // If there are no commits, all files are Untracked.
        if (commits.isEmpty()) {
            return emptyMap()
        }
        
        // For a simple mock, we simulate: any file in any commit is tracked.
        // Let's store actual content hashes at commit time
        // Let's look up tracked files from a separate file: `.git_tracked_hashes.json`
        val trackedFile = File(context.filesDir, "git_tracked_hashes.json")
        if (trackedFile.exists()) {
            try {
                val json = JSONObject(trackedFile.readText())
                val map = mutableMapOf<String, String>()
                json.keys().forEach { key ->
                    map[key] = json.getString(key)
                }
                return map
            } catch (e: Exception) {
                // fallback
            }
        }
        return emptyMap()
    }

    private fun saveTrackedFilesHashes(hashes: Map<String, String>) {
        try {
            val json = JSONObject()
            hashes.forEach { (k, v) -> json.put(k, v) }
            File(context.filesDir, "git_tracked_hashes.json").writeText(json.toString())
        } catch (e: Exception) {
            // ignore
        }
    }

    fun stageFile(relativePath: String): Boolean {
        val f = File(workspaceDir, relativePath)
        if (f.exists() || getTrackedFilesHashes().containsKey(relativePath)) {
            stagedFiles.add(relativePath)
            saveGitRegistry()
            return true
        }
        return false
    }

    fun unstageFile(relativePath: String): Boolean {
        val success = stagedFiles.remove(relativePath)
        if (success) {
            saveGitRegistry()
        }
        return success
    }

    fun commit(message: String, author: String = "blackroot@hacker.io"): GitCommit? {
        if (stagedFiles.isEmpty()) return null
        
        val timestamp = System.currentTimeMillis()
        val randomBytes = (message + timestamp.toString()).toByteArray()
        val sha = calculateSHA1(randomBytes).take(7)

        val commit = GitCommit(
            hash = sha,
            message = message,
            author = author,
            timestamp = timestamp,
            changedFiles = stagedFiles.toList()
        )

        commits.add(0, commit) // Add at start (newest first)

        // Update tracked hashes
        val currentTracked = getTrackedFilesHashes().toMutableMap()
        stagedFiles.forEach { relPath ->
            val f = File(workspaceDir, relPath)
            if (f.exists()) {
                currentTracked[relPath] = calculateHash(f)
            } else {
                currentTracked.remove(relPath) // File was deleted and committed
            }
        }
        saveTrackedFilesHashes(currentTracked)

        stagedFiles.clear()
        saveGitRegistry()
        return commit
    }

    // --- Clone Real GitHub Repo via ZIP ---

    suspend fun cloneRepository(githubUrl: String, onProgress: (String) -> Unit): Result<Int> = withContext(Dispatchers.IO) {
        try {
            onProgress("Parsing repository URL...")
            // Handle standard github urls like https://github.com/username/repo
            val cleanUrl = githubUrl.trim().removeSuffix("/")
            val parts = cleanUrl.split("github.com/")
            if (parts.size < 2) {
                return@withContext Result.failure(Exception("Invalid GitHub URL. Must be like https://github.com/user/repo"))
            }
            val repoPath = parts[1] // "user/repo"
            val zipUrlString = "https://github.com/$repoPath/archive/refs/heads/main.zip"
            val fallbackZipUrlString = "https://github.com/$repoPath/archive/refs/heads/master.zip"
            
            onProgress("Connecting to GitHub API...")
            var connection = URL(zipUrlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            
            var responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                onProgress("Main branch not found. Retrying master branch...")
                connection = URL(fallbackZipUrlString).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                responseCode = connection.responseCode
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("GitHub returned response code: $responseCode"))
            }

            onProgress("Downloading repository package (ZIP)...")
            val bytesStream = BufferedInputStream(connection.inputStream)
            val zipIn = ZipInputStream(bytesStream)
            
            onProgress("Extracting files to Workspace...")
            var entry = zipIn.nextEntry
            var fileCount = 0
            
            // Clear current workspace files (except internal system state) before cloning to emulate a clean clone
            workspaceDir.listFiles()?.forEach { f ->
                if (f.name != ".git_history" && f.name != ".git") {
                    f.deleteRecursively()
                }
            }

            val prefixToRemove = repoPath.substringAfterLast("/") + "-" // "repo-main" or "repo-master"

            while (entry != null) {
                val entryName = entry.name
                // Zip file contains a parent directory like "Spoon-Knife-main/index.html"
                // We want to extract directly, stripping the first root directory segment
                val relativeSegments = entryName.split("/").drop(1)
                if (relativeSegments.isNotEmpty() && relativeSegments.first().isNotEmpty()) {
                    val relativePath = relativeSegments.joinToString("/")
                    val destFile = File(workspaceDir, relativePath)
                    
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { out ->
                            val buffer = ByteArray(4096)
                            var len: Int
                            while (zipIn.read(buffer).also { len = it } > 0) {
                                out.write(buffer, 0, len)
                            }
                        }
                        fileCount++
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            zipIn.close()
            connection.disconnect()

            // Initialize Git simulation
            stagedFiles.clear()
            commits.clear()
            val initialCommit = GitCommit(
                hash = calculateSHA1(repoPath.toByteArray()).take(7),
                message = "Cloned repository $repoPath from GitHub",
                author = "github-action@github.com",
                timestamp = System.currentTimeMillis(),
                changedFiles = listFiles().filter { !it.isDirectory }.map { it.relativePath }
            )
            commits.add(initialCommit)
            
            // Mark all files as tracked matching current files
            val tracked = mutableMapOf<String, String>()
            listFiles().filter { !it.isDirectory }.forEach { wFile ->
                tracked[wFile.relativePath] = calculateHash(wFile.file)
            }
            saveTrackedFilesHashes(tracked)
            saveGitRegistry()

            onProgress("Cloned successfully. Imported $fileCount files.")
            Result.success(fileCount)
        } catch (e: Exception) {
            Log.e("WorkspaceManager", "Clone failed", e)
            Result.failure(e)
        }
    }

    // --- Utility Hashes ---

    private fun calculateHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val bytes = file.readBytes()
            val hashBytes = digest.digest(bytes)
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    private fun calculateSHA1(bytes: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            val hashBytes = digest.digest(bytes)
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}
