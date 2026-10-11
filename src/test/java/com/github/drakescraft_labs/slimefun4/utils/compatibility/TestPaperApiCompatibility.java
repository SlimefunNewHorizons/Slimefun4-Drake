package com.github.drakescraft_labs.slimefun4.utils.compatibility;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Checks the compiled plugin against the Paper API jars of every supported version.
 * <p>
 * The plugin is compiled once, against 1.21.11, and then runs on 26.x as well. If a class,
 * method or field it uses is missing on a newer version, or a class turned into an interface,
 * the server only finds out at runtime ({@link NoSuchMethodError},
 * {@link IncompatibleClassChangeError}). This test reads the bytecode in {@code target/classes}
 * and resolves every Paper/Bukkit reference in each API jar, the way the JVM would.
 * It also fails on anything marked {@code @Deprecated(forRemoval = true)}, so removed API
 * cannot creep back in.
 * <p>
 * The jars are copied to {@code target/paper-api} by the maven-dependency-plugin.
 */
class TestPaperApiCompatibility {

    private static final Path PLUGIN_CLASSES = Paths.get("target", "classes");
    private static final Path API_JARS = Paths.get("target", "paper-api");

    private static final String[] API_PACKAGES = { "org/bukkit/", "io/papermc/paper/", "com/destroystokyo/paper/" };

    @ParameterizedTest(name = "Paper API {0}")
    @ValueSource(strings = { "1.21.11", "26.1", "26.2" })
    void testPluginLinksAgainstPaperApi(String version) throws IOException {
        Path jar = API_JARS.resolve("paper-api-" + version + ".jar");
        Assertions.assertTrue(Files.exists(jar), "Missing " + jar + ", run the build through Maven so the dependency plugin copies it");

        ApiIndex api = ApiIndex.load(jar);
        TreeSet<String> problems = new TreeSet<>();

        try (Stream<Path> files = Files.walk(PLUGIN_CLASSES)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".class"))::iterator) {
                try (InputStream in = Files.newInputStream(file)) {
                    new ClassReader(in).accept(new ReferenceCollector(api, problems), ClassReader.SKIP_FRAMES);
                }
            }
        }

        Assertions.assertTrue(problems.isEmpty(), () -> problems.size() + " problem(s) against Paper API " + version + ":\n" + String.join("\n", problems.stream().limit(100).toList()));
    }

    private static boolean isApiType(String internalName) {
        for (String prefix : API_PACKAGES) {
            if (internalName.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Every class of one Paper API jar: its hierarchy, its members and what is marked for removal.
     */
    private static final class ApiIndex {

        private final Map<String, ApiClass> classes = new HashMap<>();

        static ApiIndex load(Path jar) throws IOException {
            ApiIndex index = new ApiIndex();

            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();

                    if (entry.getName().endsWith(".class") && !entry.getName().endsWith("module-info.class")) {
                        try (InputStream in = zip.getInputStream(entry)) {
                            ApiClass apiClass = new ApiClass();
                            new ClassReader(in).accept(apiClass, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                            index.classes.put(apiClass.name, apiClass);
                        }
                    }
                }
            }

            return index;
        }

        ApiClass get(String name) {
            return classes.get(name);
        }

        /**
         * @return null if the member resolves, otherwise why it does not
         */
        String resolve(String owner, String name, String descriptor, boolean method) {
            Boolean forRemoval = find(owner, name, descriptor, method, new TreeSet<>());

            if (forRemoval == null) {
                return "missing";
            }

            return forRemoval ? "marked for removal" : null;
        }

        /**
         * Looks the member up like the JVM does: the class, its superclasses and its interfaces.
         *
         * @return null if not found, otherwise whether it is marked for removal
         */
        private Boolean find(String owner, String name, String descriptor, boolean method, TreeSet<String> visited) {
            if (!visited.add(owner)) {
                return null;
            }

            ApiClass apiClass = classes.get(owner);

            if (apiClass == null) {
                return findOutsideApi(owner, name, descriptor, method);
            }

            Map<String, Boolean> members = method ? apiClass.methods : apiClass.fields;
            Boolean own = members.get(method ? name + descriptor : name);

            if (own != null) {
                return own;
            }

            List<String> parents = new ArrayList<>(apiClass.interfaces);

            if (apiClass.superName != null) {
                parents.add(0, apiClass.superName);
            } else if (apiClass.isInterface()) {
                parents.add("java/lang/Object");
            }

            for (String parent : parents) {
                Boolean inherited = find(parent, name, descriptor, method, visited);

                if (inherited != null) {
                    return inherited;
                }
            }

            return null;
        }

        /**
         * Members inherited from outside the jar: JDK classes are checked by reflection.
         * Other libraries (Adventure, Guava) are not part of this check, so they count as found.
         */
        private static Boolean findOutsideApi(String owner, String name, String descriptor, boolean method) {
            if (!owner.startsWith("java/")) {
                return Boolean.FALSE;
            }

            try {
                Class<?> type = Class.forName(owner.replace('/', '.'), false, TestPaperApiCompatibility.class.getClassLoader());

                for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                    if (method) {
                        for (Method m : current.getDeclaredMethods()) {
                            if (m.getName().equals(name) && Type.getMethodDescriptor(m).equals(descriptor)) {
                                return Boolean.FALSE;
                            }
                        }
                    } else {
                        for (Field f : current.getDeclaredFields()) {
                            if (f.getName().equals(name)) {
                                return Boolean.FALSE;
                            }
                        }
                    }
                }

                // Interface methods of JDK types, e.g. Comparable#compareTo on an API enum
                for (Method m : type.getMethods()) {
                    if (method && m.getName().equals(name) && Type.getMethodDescriptor(m).equals(descriptor)) {
                        return Boolean.FALSE;
                    }
                }

                return null;
            } catch (ClassNotFoundException x) {
                return null;
            }
        }
    }

    private static final class ApiClass extends ClassVisitor {

        private String name;
        private String superName;
        private int access;
        private boolean forRemoval;
        private final List<String> interfaces = new ArrayList<>();
        private final Map<String, Boolean> methods = new HashMap<>();
        private final Map<String, Boolean> fields = new HashMap<>();

        ApiClass() {
            super(Opcodes.ASM9);
        }

        boolean isInterface() {
            return (access & Opcodes.ACC_INTERFACE) != 0;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.name = name;
            this.access = access;
            this.superName = superName;

            if (interfaces != null) {
                this.interfaces.addAll(List.of(interfaces));
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return forRemovalVisitor(descriptor, () -> forRemoval = true);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            String key = name + descriptor;
            methods.put(key, Boolean.FALSE);

            return new MethodVisitor(Opcodes.ASM9) {

                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    return forRemovalVisitor(annotation, () -> methods.put(key, Boolean.TRUE));
                }
            };
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            fields.put(name, Boolean.FALSE);

            return new FieldVisitor(Opcodes.ASM9) {

                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    return forRemovalVisitor(annotation, () -> fields.put(name, Boolean.TRUE));
                }
            };
        }

        private static AnnotationVisitor forRemovalVisitor(String descriptor, Runnable onForRemoval) {
            if (!"Ljava/lang/Deprecated;".equals(descriptor)) {
                return null;
            }

            return new AnnotationVisitor(Opcodes.ASM9) {

                @Override
                public void visit(String name, Object value) {
                    if ("forRemoval".equals(name) && Boolean.TRUE.equals(value)) {
                        onForRemoval.run();
                    }
                }
            };
        }
    }

    /**
     * Collects every reference from one plugin class into the API and resolves it.
     */
    private static final class ReferenceCollector extends ClassVisitor {

        private final ApiIndex api;
        private final TreeSet<String> problems;
        private String className;

        ReferenceCollector(ApiIndex api, TreeSet<String> problems) {
            super(Opcodes.ASM9);
            this.api = api;
            this.problems = problems;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;

            if (superName != null) {
                checkClass(superName, false, true);
            }

            if (interfaces != null) {
                for (String type : interfaces) {
                    checkClass(type, true, true);
                }
            }
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                    checkMember(owner, methodName, methodDescriptor, true, isInterface);
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                    checkMember(owner, fieldName, fieldDescriptor, false, null);
                }

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    checkType(Type.getObjectType(type));
                }

                @Override
                public void visitLdcInsn(Object value) {
                    if (value instanceof Type type && type.getSort() == Type.OBJECT) {
                        checkType(type);
                    }
                }

                @Override
                public void visitInvokeDynamicInsn(String dynamicName, String dynamicDescriptor, Handle bootstrap, Object... arguments) {
                    // Method references such as Material::isBlock
                    for (Object argument : arguments) {
                        if (argument instanceof Handle handle) {
                            boolean method = handle.getTag() >= Opcodes.H_INVOKEVIRTUAL;
                            checkMember(handle.getOwner(), handle.getName(), handle.getDesc(), method, method ? handle.isInterface() : null);
                        }
                    }
                }
            };
        }

        private void checkType(Type type) {
            Type element = type.getSort() == Type.ARRAY ? type.getElementType() : type;

            if (element.getSort() == Type.OBJECT) {
                checkClass(element.getInternalName(), null, false);
            }
        }

        /**
         * @param expectInterface
         *            Whether the plugin uses the type as an interface, or null if it does not matter
         */
        private void checkClass(String type, Boolean expectInterface, boolean extended) {
            if (!isApiType(type)) {
                return;
            }

            ApiClass apiClass = api.get(type);

            if (apiClass == null) {
                problems.add(className + " -> " + type + ": class missing");
            } else if (expectInterface != null && apiClass.isInterface() != expectInterface) {
                problems.add(className + " -> " + type + ": is " + (apiClass.isInterface() ? "an interface" : "a class") + " now");
            } else if (apiClass.forRemoval && extended) {
                problems.add(className + " -> " + type + ": class marked for removal");
            }
        }

        private void checkMember(String owner, String name, String descriptor, boolean method, Boolean isInterface) {
            if (owner.startsWith("[") || !isApiType(owner)) {
                return;
            }

            ApiClass apiClass = api.get(owner);

            if (apiClass == null) {
                problems.add(className + " -> " + owner + ": class missing");
                return;
            }

            if (isInterface != null && apiClass.isInterface() != isInterface) {
                problems.add(className + " -> " + owner + '.' + name + ": owner is " + (apiClass.isInterface() ? "an interface" : "a class") + " now");
                return;
            }

            String problem = api.resolve(owner, name, descriptor, method);

            if (problem != null) {
                problems.add(className + " -> " + owner + '.' + name + (method ? descriptor : "") + ": " + problem);
            }
        }
    }
}
