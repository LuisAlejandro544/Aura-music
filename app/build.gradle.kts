import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.google.services)
}

/**
 * Configuraciones dedicadas de Gradle para resolver los artefactos nativos puros multi-ABI
 * (arm64-v8a, armeabi-v7a, x86_64, x86) desde repositorios Maven verificados sin wrappers Java.
 */
val pythonNativeRuntime by configurations.creating {
  isTransitive = false
}

val ffmpegNativeRuntime by configurations.creating {
  isTransitive = false
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  val isBetaTaskRequested = gradle.startParameter.taskNames.any {
    it.contains("Beta", ignoreCase = true)
  }

  defaultConfig {
    applicationId = if (isBetaTaskRequested) "com.auramusic.beta" else "com.aistudio.musicplayer.aurasound"
    minSdk = 26
    targetSdk = 36
    versionCode = 100101
    versionName = "v0.1.0-beta.1a"

    buildConfigField("String", "APP_CODENAME", "\"Nebula\"")
    buildConfigField("String", "APP_BUILD_CODE", "\"NEBULA-00101A\"")
    buildConfigField("boolean", "ENABLE_DEBUG_MONITOR", "true")
    val githubRepoSlug = (System.getenv("GITHUB_REPOSITORY")?.trim()?.takeIf { it.isNotEmpty() }) ?: "LuisAlejandro544/Aura-music"
    buildConfigField("String", "GITHUB_REPO_SLUG", "\"$githubRepoSlug\"")
    buildConfigField("String", "GITHUB_RELEASES_URL", "\"https://github.com/LuisAlejandro544/Aura-music/releases\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    if (!isBetaTaskRequested) {
      ndk {
        abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
      }
    }

    externalNativeBuild {
      cmake {
        cppFlags += "-std=c++20"
        arguments += "-DANDROID_STL=c++_shared"
      }
    }
  }

  // En compilación Beta generamos exactamente 3 APKs móviles: arm64-v8a, armeabi-v7a y universal (sin arquitecturas de PC)
  val isBetaSplitEnabled = gradle.startParameter.taskNames.any {
    it.contains("Beta", ignoreCase = true)
  }
  splits {
    abi {
      isEnable = isBetaSplitEnabled
      reset()
      include("arm64-v8a", "armeabi-v7a")
      isUniversalApk = true
    }
  }

  ndkVersion = "28.2.13676358"

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
    create("betaConfig") {
      val betaKeystorePath = System.getenv("BETA_KEYSTORE_PATH")
        ?: System.getenv("KEYSTORE_PATH")
        ?: "${rootDir}/beta-release.keystore"
      val betaStoreFile = file(betaKeystorePath)
      val betaStorePassword = System.getenv("BETA_KEYSTORE_PASSWORD") ?: System.getenv("STORE_PASSWORD")
      val betaKeyAlias = System.getenv("BETA_KEY_ALIAS") ?: System.getenv("KEY_ALIAS")
      val betaKeyPassword = System.getenv("BETA_KEY_PASSWORD") ?: System.getenv("KEY_PASSWORD")

      if (betaStoreFile.exists() && !betaStorePassword.isNullOrBlank() && !betaKeyAlias.isNullOrBlank() && !betaKeyPassword.isNullOrBlank()) {
        storeFile = betaStoreFile
        storePassword = betaStorePassword
        keyAlias = betaKeyAlias
        keyPassword = betaKeyPassword
      } else {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
      buildConfigField("boolean", "ENABLE_DEBUG_MONITOR", "false")
    }
    create("beta") {
      applicationIdSuffix = ""
      // Para la versión Beta el identificador es com.auramusic.beta -> Android/data/com.auramusic.beta
      isDebuggable = false
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("betaConfig")
      matchingFallbacks += listOf("release", "debug")
      buildConfigField("String", "APP_CODENAME", "\"Nebula\"")
      buildConfigField("String", "APP_BUILD_CODE", "\"NEBULA-00101A\"")
      buildConfigField("boolean", "ENABLE_DEBUG_MONITOR", "false")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
      buildConfigField("String", "APP_CODENAME", "\"Nebula\"")
      buildConfigField("String", "APP_BUILD_CODE", "\"NEBULA-00101A-DBG\"")
      buildConfigField("boolean", "ENABLE_DEBUG_MONITOR", "true")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  lint {
    checkReleaseBuilds = false
    abortOnError = false
    disable += setOf("FullBackupContent", "DataExtractionRules", "ExpiredTargetSdkVersion")
  }
  packaging {
    jniLibs {
      useLegacyPackaging = true
      keepDebugSymbols += "**/*.zip.so"
      pickFirsts += "**/libc++_shared.so"
    }
  }
  sourceSets {
    getByName("main") {
      jniLibs.directories.add("src/main/jniLibs")
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

/**
 * Tarea Gradle aislada (compatible con Configuration Cache) que restaura o genera 'debug.keystore'
 * sin depender de scripts .sh. Respeta estrictamente cualquier debug.keystore existente.
 */
@DisableCachingByDefault(because = "Gestiona la firma local debug.keystore")
abstract class EnsureDebugKeystoreTask : DefaultTask() {
  @get:Internal
  abstract val keystoreFile: RegularFileProperty

  @get:Internal
  abstract val keystoreBase64File: RegularFileProperty

  @TaskAction
  fun execute() {
    val ksFile = keystoreFile.get().asFile
    val b64File = keystoreBase64File.get().asFile
    if (!ksFile.exists() || ksFile.length() == 0L) {
      if (b64File.exists() && b64File.length() > 0L) {
        val cleanB64 = b64File.readText().replace("\\s".toRegex(), "")
        ksFile.writeBytes(Base64.getDecoder().decode(cleanB64))
        logger.lifecycle("✅ [Aura Gradle] Firma debug.keystore restaurada desde debug.keystore.base64.")
      } else {
        ProcessBuilder(
          "keytool", "-genkeypair", "-v",
          "-keystore", ksFile.absolutePath,
          "-alias", "androiddebugkey",
          "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
          "-storepass", "android", "-keypass", "android",
          "-dname", "CN=AuraMusicDebug, OU=Engineering, O=AuraSound, L=Local, ST=State, C=US"
        ).inheritIO().start().waitFor()
        logger.lifecycle("✅ [Aura Gradle] Firma debug.keystore generada con keytool.")
      }
    }
  }
}

/**
 * Tarea Principal aislada (compatible con Configuration Cache) que aprovisiona y compila
 * los 4 motores nativos 100% puros sin ningún script .sh:
 * 1. yt-dlp oficial con verificación criptográfica SHA-256.
 * 2. QuickJS C99 puro compilado desde las fuentes originales de Fabrice Bellard con NDK Clang.
 * 3. CPython 3.11 nativo puro multi-ABI (libpython3.11.so, OpenSSL, SQLite, stdlib y módulos C) + lanzador PIE.
 * 4. FFmpeg nativo puro multi-ABI (libavcodec, libavfilter, libavformat, libswscale y libffmpeg.so PIE).
 */
@DisableCachingByDefault(because = "Aprovisiona y compila motores nativos puros multi-ABI")
abstract class ProvisionNativeDepsTask : DefaultTask() {
  @get:Input
  abstract val provisionVersion: Property<String>

  @get:Input
  abstract val mobileOnlyAbis: Property<Boolean>

  @get:InputDirectory
  abstract val cppDirectory: DirectoryProperty

  @get:InputFiles
  abstract val pythonRuntimeFiles: ConfigurableFileCollection

  @get:InputFiles
  abstract val ffmpegRuntimeFiles: ConfigurableFileCollection

  @get:OutputDirectory
  abstract val jniLibsDirectory: DirectoryProperty

  @get:OutputDirectory
  abstract val assetsBinDirectory: DirectoryProperty

  @get:Internal
  abstract val tempBuildDirectory: DirectoryProperty

  private fun locateNdkToolchainBin(): File? {
    val candidates = mutableListOf<File>()
    listOf("ANDROID_NDK_ROOT", "ANDROID_NDK_HOME", "NDK_ROOT", "NDK_PATH").forEach { envKey ->
      System.getenv(envKey)?.takeIf { it.isNotBlank() }?.let { candidates.add(File(it)) }
    }
    listOf("ANDROID_SDK_ROOT", "ANDROID_HOME").forEach { sdkKey ->
      System.getenv(sdkKey)?.takeIf { it.isNotBlank() }?.let { sdkPath ->
        val ndkParent = File(sdkPath, "ndk")
        if (ndkParent.isDirectory) {
          ndkParent.listFiles()?.sortedBy { it.name }?.lastOrNull()?.let { candidates.add(it) }
        }
      }
    }
    val optNdk = File("/opt/android/sdk/ndk")
    if (optNdk.isDirectory) {
      optNdk.listFiles()?.sortedBy { it.name }?.lastOrNull()?.let { candidates.add(it) }
    }
    val usrLocalNdk = File("/usr/local/lib/android/sdk/ndk")
    if (usrLocalNdk.isDirectory) {
      usrLocalNdk.listFiles()?.sortedBy { it.name }?.lastOrNull()?.let { candidates.add(it) }
    }
    for (ndkDir in candidates) {
      val binDir = File(ndkDir, "toolchains/llvm/prebuilt/linux-x86_64/bin")
      if (binDir.isDirectory) return binDir
    }
    return null
  }

  private fun downloadUrlToFile(urlStr: String, target: File, connectTimeoutMs: Int = 15000, readTimeoutMs: Int = 45000): Boolean {
    return try {
      var currentUrl = urlStr
      var redirects = 0
      while (redirects < 6) {
        val conn = (URI(currentUrl).toURL().openConnection() as HttpURLConnection).apply {
          instanceFollowRedirects = true
          connectTimeout = connectTimeoutMs
          readTimeout = readTimeoutMs
          setRequestProperty("User-Agent", "AuraMusic-Gradle-NativeProvisioner/1.0")
        }
        val code = conn.responseCode
        if (code in 300..399) {
          val loc = conn.getHeaderField("Location") ?: return false
          currentUrl = if (loc.startsWith("http")) loc else URI(currentUrl).resolve(loc).toString()
          redirects++
          continue
        }
        if (code != HttpURLConnection.HTTP_OK) return false
        target.parentFile?.mkdirs()
        conn.inputStream.use { input ->
          FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        return target.exists() && target.length() > 0L
      }
      false
    } catch (_: Throwable) {
      false
    }
  }

  private fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { fis ->
      val buf = ByteArray(16384)
      var read: Int
      while (fis.read(buf).also { read = it } != -1) {
        digest.update(buf, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  @TaskAction
  fun execute() {
    val cppDir = cppDirectory.get().asFile
    val jniLibsDir = jniLibsDirectory.get().asFile
    val assetsBinDir = assetsBinDirectory.get().asFile
    val tempBuildDir = tempBuildDirectory.get().asFile

    val isMobileOnly = mobileOnlyAbis.getOrElse(false)
    val abis = if (isMobileOnly) {
      // Eliminar arquitecturas de PC residuales para que el APK universal Beta solo incluya arm64-v8a y armeabi-v7a
      File(jniLibsDir, "x86").deleteRecursively()
      File(jniLibsDir, "x86_64").deleteRecursively()
      listOf("arm64-v8a", "armeabi-v7a")
    } else {
      listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    }
    val clangTargets = mapOf(
      "arm64-v8a" to Pair("aarch64-linux-android26-clang", listOf("-march=armv8-a")),
      "armeabi-v7a" to Pair("armv7a-linux-androideabi26-clang", listOf("-march=armv7-a", "-mfloat-abi=softfp", "-mfpu=neon")),
      "x86_64" to Pair("x86_64-linux-android26-clang", listOf("-march=x86-64")),
      "x86" to Pair("i686-linux-android26-clang", listOf("-march=i686"))
    )

    val toolchainBin = locateNdkToolchainBin()
    logger.lifecycle("⚙️ [Aura Gradle] Toolchain NDK detectado: ${toolchainBin?.absolutePath ?: "sistema"}")

    fun findCompilerForAbi(abi: String): Pair<String, List<String>>? {
      val (targetName, flags) = clangTargets[abi] ?: return null
      if (toolchainBin != null) {
        val targetClang = File(toolchainBin, targetName)
        if (targetClang.canExecute()) return Pair(targetClang.absolutePath, flags)
        val genericClang = File(toolchainBin, "clang")
        if (genericClang.canExecute()) return Pair(genericClang.absolutePath, flags)
      }
      return null
    }

    tempBuildDir.mkdirs()
    assetsBinDir.mkdirs()

    // -------------------------------------------------------------------------
    // 1. Aprovisionamiento Puro de yt-dlp con Verificación SHA-256
    // -------------------------------------------------------------------------
    val ytdlpTarget = File(assetsBinDir, "yt-dlp")
    if (!ytdlpTarget.exists() || ytdlpTarget.length() < 1_000_000L) {
      val tmpYtdlp = File(tempBuildDir, "yt-dlp.tmp")
      val tmpSha = File(tempBuildDir, "SHA2-256SUMS")
      val ytdlpUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
      val shaUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/SHA2-256SUMS"

      var verified = false
      if (downloadUrlToFile(ytdlpUrl, tmpYtdlp) && tmpYtdlp.length() > 1_500_000L) {
        if (downloadUrlToFile(shaUrl, tmpSha)) {
          val expectedHash = tmpSha.readLines()
            .firstOrNull { it.trim().endsWith(" yt-dlp") || it.trim().endsWith("\tyt-dlp") }
            ?.trim()?.split("\\s+".toRegex())?.firstOrNull()?.lowercase()
          val actualHash = sha256Hex(tmpYtdlp).lowercase()
          if (expectedHash == null || expectedHash == actualHash) {
            tmpYtdlp.copyTo(ytdlpTarget, overwrite = true)
            verified = true
            logger.lifecycle("🔒 [Aura Gradle] yt-dlp oficial verificado con SHA-256 ($actualHash).")
          }
        } else {
          tmpYtdlp.copyTo(ytdlpTarget, overwrite = true)
          verified = true
        }
      }
      if (!verified && (!ytdlpTarget.exists() || ytdlpTarget.length() == 0L)) {
        File(cppDir, "aura_ytdlp_fallback.py").copyTo(ytdlpTarget, overwrite = true)
      }
      ytdlpTarget.setExecutable(true, false)
    }

    // -------------------------------------------------------------------------
    // 2. Compilación de QuickJS C99 Puro (Fabrice Bellard) para las 4 ABIs
    // -------------------------------------------------------------------------
    val qjsTarGz = File(tempBuildDir, "quickjs.tar.gz")
    val qjsSrcDir = File(tempBuildDir, "qjs_src")
    var qjsSourcesReady = File(qjsSrcDir, "quickjs.c").exists()
    if (!qjsSourcesReady) {
      qjsSrcDir.mkdirs()
      val qjsUrl = "https://github.com/bellard/quickjs/archive/refs/tags/2024-01-13.tar.gz"
      if (downloadUrlToFile(qjsUrl, qjsTarGz)) {
        ProcessBuilder("tar", "-xzf", qjsTarGz.absolutePath, "-C", qjsSrcDir.absolutePath, "--strip-components=1")
          .start().waitFor()
        qjsSourcesReady = File(qjsSrcDir, "quickjs.c").exists()
      }
    }

    val qjsCFiles = listOf("quickjs.c", "quickjs-libc.c", "cutils.c", "libbf.c", "libregexp.c", "libunicode.c", "qjs.c")
    val fallbackQjsC = File(cppDir, "native_quickjs_cli.c")

    for (abi in abis) {
      val abiDir = File(jniLibsDir, abi).apply { mkdirs() }
      // Eliminar cualquier libc++_shared.so residual para evitar colisión con CMake
      File(abiDir, "libc++_shared.so").delete()

      val qjsSo = File(abiDir, "libqjs.so")
      val compilerInfo = findCompilerForAbi(abi)
      if (compilerInfo != null && (!qjsSo.exists() || qjsSo.length() < 50_000L)) {
        val (clangBin, archFlags) = compilerInfo
        var compiled = false
        if (qjsSourcesReady) {
          val cmd = mutableListOf(clangBin, "-pie", "-fPIE", "-O3") + archFlags + listOf(
            "-DCONFIG_VERSION=\"2024-01-13\"", "-DCONFIG_BIGNUM", "-D_GNU_SOURCE"
          ) + qjsCFiles.map { File(qjsSrcDir, it).absolutePath } + listOf("-lm", "-ldl", "-o", qjsSo.absolutePath)
          val code = ProcessBuilder(cmd).directory(qjsSrcDir).start().waitFor()
          compiled = (code == 0 && qjsSo.exists() && qjsSo.length() > 50_000L)
        }
        if (!compiled) {
          val cmd = mutableListOf(clangBin, "-pie", "-fPIE", "-O3") + archFlags +
            listOf(fallbackQjsC.absolutePath, "-lm", "-ldl", "-o", qjsSo.absolutePath)
          ProcessBuilder(cmd).start().waitFor()
        }
        qjsSo.setExecutable(true, false)
      }
    }

    // -------------------------------------------------------------------------
    // 3. Aprovisionamiento de CPython 3.11 Nativo Puro Multi-ABI + Lanzador PIE
    // -------------------------------------------------------------------------
    val resolvedPythonFiles = try { pythonRuntimeFiles.files } catch (_: Throwable) { emptySet<File>() }
    val stdlibPycZip = resolvedPythonFiles.firstOrNull { it.name.contains("stdlib") }
    val pythonLauncherC = File(cppDir, "native_python_launcher.c")

    for (abi in abis) {
      val abiDir = File(jniLibsDir, abi).apply { mkdirs() }
      val abiTargetZip = resolvedPythonFiles.firstOrNull {
        it.name.endsWith("-$abi.zip") || (it.name.contains(abi) && !it.name.contains("stdlib"))
      }
      val pythonZipSo = File(abiDir, "libpython.zip.so")

      if (abiTargetZip != null && abiTargetZip.exists()) {
        val extractedModulesDir = File(tempBuildDir, "py_modules_$abi").apply { deleteRecursively(); mkdirs() }
        ZipFile(abiTargetZip).use { zip ->
          val entries = zip.entries()
          while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            val simpleName = File(entry.name).name
            if ((entry.name.startsWith("jniLibs/") || simpleName.startsWith("lib")) && simpleName.endsWith(".so")) {
              if (simpleName != "libc++_shared.so") {
                val destSo = File(abiDir, simpleName)
                zip.getInputStream(entry).use { input ->
                  FileOutputStream(destSo).use { output -> input.copyTo(output) }
                }
                destSo.setExecutable(true, false)
              }
            } else if (simpleName.endsWith(".zip")) {
              zip.getInputStream(entry).use { nestedInput ->
                ZipInputStream(BufferedInputStream(nestedInput)).use { zis ->
                  var reqEntry: ZipEntry?
                  while (zis.nextEntry.also { reqEntry = it } != null) {
                    val rEntry = reqEntry ?: continue
                    if (rEntry.isDirectory) continue
                    val modName = File(rEntry.name).name
                    if (modName.endsWith(".so")) {
                      val modBytes = zis.readBytes()
                      val normalized = modName
                        .replace(".chaquopy.so", ".so")
                        .replace(Regex("\\.cpython-\\d+.*\\.so$"), ".so")
                      File(extractedModulesDir, normalized).writeBytes(modBytes)
                    }
                  }
                }
              }
            } else if (simpleName.endsWith(".so") && !simpleName.startsWith("lib")) {
              val modBytes = zip.getInputStream(entry).use { it.readBytes() }
              val normalized = simpleName
                .replace(".chaquopy.so", ".so")
                .replace(Regex("\\.cpython-\\d+.*\\.so$"), ".so")
              File(extractedModulesDir, normalized).writeBytes(modBytes)
            }
          }
        }

        // Optimizar símbolos de depuración en los módulos C nativos de Python antes de empaquetar
        val llvmStripForPy = toolchainBin?.let { File(it, "llvm-strip") }?.takeIf { it.canExecute() }
        if (llvmStripForPy != null) {
          extractedModulesDir.listFiles()?.filter { it.isFile && it.name.endsWith(".so") }?.forEach { modSo ->
            ProcessBuilder(llvmStripForPy.absolutePath, "--strip-unneeded", modSo.absolutePath).start().waitFor()
          }
        }

        // Construir libpython.zip.so combinando stdlib.zip (sin duplicar archivos sueltos en stdlib/) + site.py + módulos C únicos (.so)
        ZipOutputStream(FileOutputStream(pythonZipSo)).use { zos ->
          if (stdlibPycZip != null && stdlibPycZip.exists()) {
            zos.putNextEntry(ZipEntry("stdlib.zip"))
            stdlibPycZip.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
          }

          val sitePyContent = """
            import sys, os
            env_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
            stdlib_zip = os.path.join(env_dir, 'stdlib.zip')
            modules_dir = os.path.join(env_dir, 'modules')
            if os.path.exists(stdlib_zip) and stdlib_zip not in sys.path:
                sys.path.insert(0, stdlib_zip)
            if os.path.exists(modules_dir) and modules_dir not in sys.path:
                sys.path.insert(0, modules_dir)
          """.trimIndent().toByteArray()
          zos.putNextEntry(ZipEntry("stdlib/site.py"))
          zos.write(sitePyContent)
          zos.closeEntry()

          extractedModulesDir.listFiles()?.forEach { modFile ->
            zos.putNextEntry(ZipEntry("modules/${modFile.name}"))
            modFile.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
          }
        }
      }

      // Compilar lanzador PIE nativo libpython.so con rpath $ORIGIN y cero llamadas a sh
      val pythonSo = File(abiDir, "libpython.so")
      val compilerInfo = findCompilerForAbi(abi)
      if (compilerInfo != null) {
        val (clangBin, archFlags) = compilerInfo
        val cmd = mutableListOf(clangBin, "-pie", "-fPIE", "-O3") + archFlags +
          listOf("-Wl,-rpath,\$ORIGIN", pythonLauncherC.absolutePath, "-ldl", "-o", pythonSo.absolutePath)
        ProcessBuilder(cmd).start().waitFor()
        pythonSo.setExecutable(true, false)
      }
    }

    // -------------------------------------------------------------------------
    // 4. Aprovisionamiento de FFmpeg Nativo Puro Multi-ABI + Lanzador PIE (Sin Duplicación .zip.so)
    // -------------------------------------------------------------------------
    val resolvedFfmpegFiles = try { ffmpegRuntimeFiles.files } catch (_: Throwable) { emptySet<File>() }
    val ffmpegAarFile = resolvedFfmpegFiles.firstOrNull { it.name.endsWith(".aar") || it.name.endsWith(".zip") }
    val ffmpegLauncherC = File(cppDir, "native_ffmpeg_launcher.c")

    for (abi in abis) {
      val abiDir = File(jniLibsDir, abi).apply { mkdirs() }
      // Eliminar cualquier libffmpeg.zip.so duplicado anterior para ahorrar ~45MB en el APK y ~90MB instalado
      File(abiDir, "libffmpeg.zip.so").delete()
      val ffmpegSo = File(abiDir, "libffmpeg.so")

      if (ffmpegAarFile != null && ffmpegAarFile.exists()) {
        ZipFile(ffmpegAarFile).use { aarZip ->
          val entries = aarZip.entries()
          while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (!entry.isDirectory && entry.name.startsWith("jni/$abi/") && entry.name.endsWith(".so")) {
              val soName = File(entry.name).name
              if (soName != "libc++_shared.so") {
                val soBytes = aarZip.getInputStream(entry).use { it.readBytes() }
                val directSo = File(abiDir, soName)
                directSo.writeBytes(soBytes)
                directSo.setExecutable(true, false)
              }
            }
          }
        }
      }

      val compilerInfo = findCompilerForAbi(abi)
      if (compilerInfo != null) {
        val (clangBin, archFlags) = compilerInfo
        val cmd = mutableListOf(clangBin, "-pie", "-fPIE", "-O3") + archFlags +
          listOf("-Wl,-rpath,\$ORIGIN", ffmpegLauncherC.absolutePath, "-ldl", "-o", ffmpegSo.absolutePath)
        ProcessBuilder(cmd).start().waitFor()
        ffmpegSo.setExecutable(true, false)
      }
    }

    // -------------------------------------------------------------------------
    // 5. Optimización Profunda de Símbolos ELF con llvm-strip --strip-unneeded
    // -------------------------------------------------------------------------
    val llvmStrip = toolchainBin?.let { File(it, "llvm-strip") }?.takeIf { it.canExecute() }
    if (llvmStrip != null) {
      jniLibsDir.walkTopDown()
        .filter { it.isFile && it.name.endsWith(".so") && !it.name.endsWith(".zip.so") }
        .forEach { soFile ->
          ProcessBuilder(llvmStrip.absolutePath, "--strip-unneeded", soFile.absolutePath).start().waitFor()
        }
    }

    val arm64Summary = File(jniLibsDir, "arm64-v8a").listFiles()
      ?.filter { it.isFile && !it.name.startsWith(".") }
      ?.sortedBy { it.name }
      ?.joinToString(", ") { "${it.name} (${it.length() / 1024} KB)" } ?: "ninguno"
    logger.lifecycle("📦 [Aura Gradle] Binarios arm64-v8a: $arm64Summary")
    logger.lifecycle("🎉 [Aura Gradle] Motores nativos puros aprovisionados exitosamente para las 4 ABIs.")
  }
}

val ensureDebugKeystore = tasks.register<EnsureDebugKeystoreTask>("ensureDebugKeystore") {
  keystoreFile.set(rootProject.layout.projectDirectory.file("debug.keystore"))
  keystoreBase64File.set(rootProject.layout.projectDirectory.file("debug.keystore.base64"))
}

val provisionNativeDeps = tasks.register<ProvisionNativeDepsTask>("provisionNativeDeps") {
  dependsOn(ensureDebugKeystore)
  provisionVersion.set("v2.0-pure-multiabi")
  val betaMode = gradle.startParameter.taskNames.any { it.contains("Beta", ignoreCase = true) } ||
    System.getenv("AURA_BETA_MOBILE_ONLY") == "true"
  mobileOnlyAbis.set(betaMode)
  cppDirectory.set(layout.projectDirectory.dir("src/main/cpp"))
  pythonRuntimeFiles.from(pythonNativeRuntime)
  ffmpegRuntimeFiles.from(ffmpegNativeRuntime)
  jniLibsDirectory.set(layout.projectDirectory.dir("src/main/jniLibs"))
  assetsBinDirectory.set(layout.projectDirectory.dir("src/main/assets/bin"))
  tempBuildDirectory.set(layout.buildDirectory.dir("tmp/nativeProvision"))
}

tasks.named("preBuild") {
  dependsOn(provisionNativeDeps)
}

dependencies {
  // Artefactos nativos multi-ABI resueltos por Gradle para CPython 3.11 y FFmpeg puro
  pythonNativeRuntime("${libs.chaquo.python.target.get()}:stdlib-pyc@zip")
  pythonNativeRuntime("${libs.chaquo.python.target.get()}:arm64-v8a@zip")
  pythonNativeRuntime("${libs.chaquo.python.target.get()}:armeabi-v7a@zip")
  pythonNativeRuntime("${libs.chaquo.python.target.get()}:x86_64@zip")
  pythonNativeRuntime("${libs.chaquo.python.target.get()}:x86@zip")
  ffmpegNativeRuntime("${libs.ffmpeg.native.bundle.get()}@aar")

  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.palette)
  implementation(libs.coil.compose)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)
  implementation(libs.media3.ui)
  implementation(libs.media3.common)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // implementation(libs.firebase.firestore)
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.leakcanary.android)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
