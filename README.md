# multi_channel
一个groovy gradle plugin，用于gradle构建多渠道apk。
##原理
基于底包向apk中添加/assets/channel_info文件，并删除apk的META-INF目录，重新签名
		

* 比传统的productFlavors方式更高效，单个apk生成时间在20s左右。
* 比美团把渠道信息写入META-INF要安全，所有的apk都经过了完整的签名

##使用
```java
//在builid.gradle中添加maven依赖
apply plugin: 'multi_channel_plugin'
dependencies {
        classpath 'com.android.tools.build:gradle:1.3.1'
        classpath 'com.tgithubc:multi_channel_plugin:1.2'
    }
.
.
.
//在multichannel中派生子包，aBase，bBase为android.productFlavors生产的底包
multichannel {
    signingConfig = android.signingConfigs.release
    version = android.defaultConfig.versionName
    // 调试日志
    debugLog true
    //命名规则
    // abi为数字 7 = arm ；8 = arm64-v8a
    // xxx随便 {abi}{version}{channel}随便组合
    regulation = 'xxx{abi}{version}{channel}xxx.apk';

    channelConfig {
        aBase {
            // 基于aBase底包的渠道列表,渠道列表自行整理
            arm_channel = readChannelFromFile('./channel1.txt')
            arm_v8a_channel = ['000','100']
        }

        bBase {
            // 基于bBase底包的渠道列表,渠道列表自行整理
            childFlavors = readChannelFromFile('./channel2.txt')
        }
    }
}


//在代码中获取渠道信息
ChannelInfoUtils.getChannel(Context context)
```
##License

>Copyright 2011-2015 Sergey Tarasevich

>Licensed under the Apache License, Version 2.0 (the "License");
>you may not use this file except in compliance with the License.
>You may obtain a copy of the License at
>http://www.apache.org/licenses/LICENSE-2.0

>Unless required by applicable law or agreed to in writing, software
>distributed under the License is distributed on an "AS IS" BASIS,
>WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
>See the License for the specific language governing permissions and
>limitations under the License.
