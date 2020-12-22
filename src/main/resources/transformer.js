var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
var LdcInsnNode = Java.type('org.objectweb.asm.tree.LdcInsnNode');
var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');
var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');
var FloatArr = Java.type('float[]');

function float(number) {
    var array = new FloatArr(1);
    array[0] = number;
    return array[0];
}

function findMethod(methods, entry) {
    for (var i = 0; i < methods.length; i++) {
        var method = methods[i];
        if ((method.name.equals(entry.obf) || method.name.equals(entry.name)) && method.desc.equals(entry.desc)) {
            return method;
        }
    }
    return null;
}

function initializeCoreMod() {
    return {
        'unconditional breaking speed': {
            target: {
                'type': 'CLASS',
                'name': 'net.minecraft.block.Block'
            },
            transformer: function(classNode) {
                var method = findMethod(classNode.methods, {
                    obf: 'func_180647_a',
                    name: 'getPlayerRelativeBlockHardness',
                    desc: '(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/IBlockReader;Lnet/minecraft/util/math/BlockPos;)F',
                });
                var injection = new InsnList();
                injection.add(new VarInsnNode(Opcodes.ALOAD, 1));
                injection.add(new VarInsnNode(Opcodes.ALOAD, 2));
                injection.add(new VarInsnNode(Opcodes.ALOAD, 3));
                injection.add(new VarInsnNode(Opcodes.ALOAD, 4));
                injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    'dev/necauqua/mods/subpocket/SubspatialKeyItem$Interactions',
                    'forceDefaultSpeedCondition',
                    '(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/IBlockReader;Lnet/minecraft/util/math/BlockPos;)Z',
                    false));
                var skip = new LabelNode();
                injection.add(new JumpInsnNode(Opcodes.IFEQ, skip));
                injection.add(new LdcInsnNode(float(1.0 / 30.0)));
                injection.add(new InsnNode(Opcodes.FRETURN));
                injection.add(skip);

                method.instructions.insert(injection);
                return classNode;
            }
        }
    };
}
