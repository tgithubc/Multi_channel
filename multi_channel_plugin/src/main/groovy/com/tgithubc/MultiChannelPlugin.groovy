package com.tgithubc

import com.android.build.FilterData
import com.android.build.gradle.internal.scope.ApkData
import com.android.builder.model.SigningConfig
import org.apache.commons.io.FilenameUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.internal.reflect.Instantiator

import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

class MultiChannelPlugin implements Plugin<Project> {

    enum ABI {
        ABI_ARM_V8A("arm64-v8a", 8),
        ABI_ARM("armeabi", 7),

        private def code
        private def description

        ABI(def description, def code) {
            this.description = description
            this.code = code
        }
    }

    // 不同abi产品生产线
    static class ProductLine {

        // 需要生成的cpu平台信息
        ABI abi
        // 需要生成的渠道信息列表
        List<String> channelList
        // 原始底包name
        def apkName
        // 原始底包path
        def path
        // 当前channel
        def channel

        @Override
        String toString() {
            """\
        ProductLine {
            abi= ${abi},
            apkName=${apkName},
            channelList=${channelList},
            path= ${path},
        }
        """.stripMargin()
        }

        // 原始全路径path
        def getOriginalPath() {
            return path + apkName + ".apk"
        }

        // 临时处理
        def getTempFileName() {
            return path + apkName + "_" + channel + "_temp"
        }
    }

    // 写入渠道信息根目录
    static final def CHANNEL_DIR = "/assets"
    // 写入渠道信息文件
    static final def CHANNEL_FILE = "/assets/channel_info"
    // 默认系统配置文件中签名jarsigner
    def DEFAULT_JARSIGNER_EXE = System.properties.'java.home' + File.separator + ".."\
                                        + File.separator + "bin"\
                                        + File.separator + "jarsigner"
    // java签名，4字节对齐
    def jarsigner_path, zipalign_path

    @Override
    void apply(Project project) {
        // 扩展multichannel闭包
        project.extensions.create("multichannel", MultiChannelExt)
        // 嵌套扩展channelConfig闭包
        project.multichannel.extensions.channelConfig \
                    = project.container(Flavors) { String name ->
            // DSL new
            Flavors flavors = project.gradle.services.get(Instantiator).newInstance(Flavors, name)
            return flavors
        }

        project.afterEvaluate {

            // 指定/默认 path 赋值
            if (project.multichannel.jarsignerPath) {
                jarsigner_path = project.multichannel.jarsignerPath
            } else {
                jarsigner_path = DEFAULT_JARSIGNER_EXE
            }

            if (project.multichannel.debugLog) {
                println(">>> jarsigner path: " + jarsigner_path)
            }

            if (project.multichannel.zipalignPath) {
                zipalign_path = project.multichannel.zipalignPath
            } else {
                zipalign_path = "${project.android.getSdkDirectory().getAbsolutePath()}"\
                                        + File.separator + "build-tools"\
                                        + File.separator + project.android.buildToolsVersion\
                                        + File.separator + "zipalign"
            }

            if (project.multichannel.debugLog) {
                println(">>> zipalign path: " + zipalign_path)
            }

            final def variants = project.android.applicationVariants
            variants.all { variant ->

                def flavorName = variant.properties.get('flavorName')
                variant.assemble.doLast {
                    project.multichannel.channelConfig.each() { config ->
                        println(config)
                        // 大flavor集合 匹配 release
                        if (flavorName != config.name) {
                            return
                        }
                        // 原始apk
                        variant.getOutputs().each() { file ->
                            ApkData apkData = file.getApkData()
                            FilterData filterData = apkData.getFilters().first()
                            Path path = Paths.get(file.getOutputFile().getAbsolutePath())
                            def product = new ProductLine()
                            product.apkName = FilenameUtils.removeExtension(path.getFileName().toString())
                            if (filterData.getIdentifier() == ABI.ABI_ARM.description) {
                                product.abi = ABI.ABI_ARM
                                product.channelList = config.arm_channel
                            } else if (filterData.getIdentifier() == ABI.ABI_ARM_V8A.description) {
                                product.abi = ABI.ABI_ARM_V8A
                                product.channelList = config.arm_v8a_channel
                            }
                            product.path = path.getParent().toString() + File.separator
                            produce(project, product)
                        }
                    }
                }
            }
        }
    }

    def produce(Project project, ProductLine productLine) {
        productLine.channelList.each() { channel ->
            try {
                productLine.channel = channel
                createApkWithChannel(project, productLine)
            } catch(Exception e) {
                e.printStackTrace()
            }
        }
    }

    void createApkWithChannel(Project project, ProductLine productLine) throws IOException, InterruptedException {
        SigningConfig signConfig = project.multichannel.signingConfig
        if (!signConfig || !signConfig.isSigningReady()) {
            throw new ProjectConfigurationException("signing config == null", null)
        }

        if (project.multichannel.debugLog) {
            println(">>> createApkWithChannel "
                        + "\n product:\n" + productLine
                        + "\n debug:" + project.multichannel.debugLog
                        + "\n regulation:" + project.multichannel.regulation
                        + "\n keystore path:" + signConfig.getStoreFile().getAbsolutePath()
                        + "\n keystore alias:" + signConfig.getKeyAlias()
                        + "\n keystore password:" + signConfig.getStorePassword()
                        + "\n keystore key password:" + signConfig.getKeyPassword()
            )
        }

        // 把原始apk转zip临时文件
        File originalFile = new File(productLine.getOriginalPath())
        File tempZipFile = new File(productLine.getTempFileName() + ".zip")
        Files.copy(originalFile.toPath(), tempZipFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        // delete META-INF
        FileSystem zipFileSystem = null
        try {
            zipFileSystem = createZipFileSystem(tempZipFile.getAbsolutePath())
            deleteEntry(zipFileSystem, "/META-INF/")
            createEntry(zipFileSystem, CHANNEL_FILE, productLine.channel)
        } finally {
            zipFileSystem?.close()
        }
        File tempApkFile = new File(productLine.getTempFileName() + ".apk")
        Files.move(tempZipFile.toPath(), tempApkFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

        // 重签名
        String signCmd = (jarsigner_path + " -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore "
                + signConfig.getStoreFile().getAbsolutePath()
                + " -storepass " + signConfig.getStorePassword()
                + " -keypass " + signConfig.getKeyPassword() + " "
                + tempApkFile.getAbsolutePath().replaceAll(" ", "\" \"") + " "
                + signConfig.getKeyAlias())
        if (execCmdAndWait(signCmd, project.multichannel.debugLog) != 0) {
            if (project.multichannel.debugLog) {
                println("jarsigner Error: " + tempApkFile.getAbsolutePath())
            }
            return
        }

        def finalApkName = project.multichannel.regulation \
                                  .replaceAll("\\{abi}", String.valueOf(productLine.abi.code)) \
                                  .replaceAll("\\{version}", project.multichannel.version) \
                                  .replaceAll("\\{channel}", productLine.channel)
        def finalApkPath = productLine.path + finalApkName
        File finalApkFile = new File(finalApkPath)
        if (finalApkFile.exists()) {
            finalApkFile.delete()
        }

        // 对齐
        String zipAlignCmd = (zipalign_path + " -v 4 "
                + tempApkFile.getAbsolutePath().replaceAll(" ", "\" \"") + " "
                + finalApkFile.getAbsolutePath().replaceAll(" ", "\" \""))
        if (execCmdAndWait(zipAlignCmd, project.multichannel.debugLog) != 0) {
            if (project.multichannel.debugLog) {
                println("zipalign Error: " + tempApkFile.getAbsolutePath())
            }
            return
        }
        tempApkFile.delete()
    }

    static void createEntry(FileSystem fileSystem, String entryName, String content) throws IOException {
        Path dirPath = fileSystem.getPath(CHANNEL_DIR)
        if (!Files.exists(dirPath)) {
            Files.createDirectory(dirPath)
        }
        Path entryPath = fileSystem.getPath(entryName)
        BufferedWriter bw = Files.newBufferedWriter(entryPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE)
        bw.with { writer ->
            writer.write(content + "-" + System.currentTimeMillis())
        }
    }

    static void deleteEntry(FileSystem fileSystem, String entryName) throws IOException {
        Path path = fileSystem.getPath(entryName)
        if (!Files.exists(path)) {
            return
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc == null) {
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                } else {
                    throw exc
                }
            }
        })
    }

    static FileSystem createZipFileSystem(String zipFilename) throws IOException {
        final Path path = Paths.get(zipFilename)
        final URI uri = URI.create("jar:file:" + path.toUri().getPath())
        def map = ["create": "true"]
        return FileSystems.newFileSystem(uri, map)
    }

    static int execCmdAndWait(String cmd, boolean showLog) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(cmd)
        if (process) {
            if (showLog) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))
                String line
                while ((line = reader.readLine())) {
                    println(">>> jarsigner : " + line)
                }
            }
            return process.waitFor()
        }
        return 0
    }
}
