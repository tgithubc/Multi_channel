package com.tgithubc

// 大车间
class Flavors {

    def name
    // 不同abi的子渠道集合
    List<String> arm_channel
    List<String> arm_v8a_channel

    Flavors(def name) {
        this.name = name
    }

    @Override
    String toString() {
        """\
        Flavors {
            name= ${name},
            armChannel=${arm_channel},
            arm_v8a_channel=${arm_v8a_channel}
        }
        """.stripMargin()
    }
}
