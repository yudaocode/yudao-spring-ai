/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.compat.opensearch;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.opensearch.client.opensearch.OpenSearchClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards every SDK class against statically linked Java 9+ platform APIs on the real VM.
 *
 * @author 芋道源码
 */
class Java8SdkApiTests {

	private final Set<String> failures = new TreeSet<>();

	private final Map<String, Boolean> members = new HashMap<>();

	private final Set<String> classes = new HashSet<>();

	@Test
	void everySdkClassUsesAvailableJava8PlatformTypesAndMembers() throws Exception {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		Path root = Paths.get(OpenSearchClient.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		assertTrue(Files.isDirectory(root), "Must scan this module's unpacked output, not the provided original jar");
		int count = 0;
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList())) {
				String source = root.relativize(path).toString();
				count++;
				new ClassReader(Files.readAllBytes(path)).accept(new ClassVisitor(Opcodes.ASM9) {
					@Override
					public void visit(int version, int access, String name, String signature, String parent,
							String[] interfaces) {
						if (parent != null)
							checkType(source, Type.getObjectType(parent));
						if (interfaces != null)
							for (String itf : interfaces)
								checkType(source, Type.getObjectType(itf));
					}

					@Override
					public FieldVisitor visitField(int access, String name, String descriptor, String signature,
							Object value) {
						checkType(source, Type.getType(descriptor));
						return null;
					}

					@Override
					public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
							String[] exceptions) {
						for (Type argument : Type.getArgumentTypes(descriptor))
							checkType(source, argument);
						checkType(source, Type.getReturnType(descriptor));
						if (exceptions != null)
							for (String exception : exceptions)
								checkType(source, Type.getObjectType(exception));
						return new MethodVisitor(Opcodes.ASM9) {
							@Override
							public void visitMethodInsn(int opcode, String owner, String method, String desc,
									boolean itf) {
								checkMember(source, owner, method, desc, false);
							}

							@Override
							public void visitFieldInsn(int opcode, String owner, String field, String desc) {
								checkMember(source, owner, field, desc, true);
							}

							@Override
							public void visitTypeInsn(int opcode, String type) {
								checkType(source, type.startsWith("[") ? Type.getType(type) : Type.getObjectType(type));
							}

							@Override
							public void visitLdcInsn(Object value) {
								if (value instanceof Type)
									checkType(source, (Type) value);
								if (value instanceof Handle)
									checkHandle(source, (Handle) value);
							}

							@Override
							public void visitInvokeDynamicInsn(String method, String desc, Handle bootstrap,
									Object... args) {
								checkHandle(source, bootstrap);
								for (Object arg : args)
									if (arg instanceof Handle)
										checkHandle(source, (Handle) arg);
							}
						};
					}
				}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			}
		}
		assertTrue(count > 6000, "The full upstream SDK payload must be present");
		assertTrue(failures.isEmpty(), String.join("\n", failures));
	}

	private boolean platform(String owner) {
		return owner.startsWith("java/") || owner.startsWith("jdk/") || owner.startsWith("sun/");
	}

	private void checkType(String source, Type type) {
		if (type.getSort() == Type.ARRAY)
			type = type.getElementType();
		if (type.getSort() != Type.OBJECT || !platform(type.getInternalName()))
			return;
		String name = type.getClassName();
		if (classes.add(name)) {
			try {
				Class.forName(name, false, null);
			}
			catch (ClassNotFoundException | LinkageError missing) {
				failures.add(source + ": missing " + name);
			}
		}
	}

	private void checkHandle(String source, Handle handle) {
		checkMember(source, handle.getOwner(), handle.getName(), handle.getDesc(),
				handle.getTag() <= Opcodes.H_PUTSTATIC);
	}

	private void checkMember(String source, String owner, String name, String descriptor, boolean field) {
		if (!platform(owner))
			return;
		String key = owner + "." + name + descriptor;
		if (members.containsKey(key))
			return;
		boolean found;
		try {
			Class<?> type = Class.forName(owner.replace('/', '.'), false, null);
			found = field ? hasField(type, name, descriptor, new HashSet<>())
					: hasMethod(type, name, descriptor, new HashSet<>());
		}
		catch (ClassNotFoundException | LinkageError missing) {
			found = false;
		}
		members.put(key, found);
		if (!found)
			failures.add(source + ": missing " + key);
	}

	private boolean hasField(Class<?> type, String name, String descriptor, Set<Class<?>> seen) {
		if (type == null || !seen.add(type))
			return false;
		for (Field field : type.getDeclaredFields()) {
			if (field.getName().equals(name) && Type.getDescriptor(field.getType()).equals(descriptor))
				return true;
		}
		if (hasField(type.getSuperclass(), name, descriptor, seen))
			return true;
		for (Class<?> parent : type.getInterfaces())
			if (hasField(parent, name, descriptor, seen))
				return true;
		return false;
	}

	private boolean hasMethod(Class<?> type, String name, String descriptor, Set<Class<?>> seen) {
		if (type == null || !seen.add(type))
			return false;
		if (type == java.lang.invoke.MethodHandle.class && ("invoke".equals(name) || "invokeExact".equals(name)))
			return true;
		if ("<init>".equals(name)) {
			for (Constructor<?> constructor : type.getDeclaredConstructors())
				if (Type.getConstructorDescriptor(constructor).equals(descriptor))
					return true;
			return false;
		}
		for (Method method : type.getDeclaredMethods())
			if (method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor))
				return true;
		if (type.isInterface() && hasMethod(Object.class, name, descriptor, seen))
			return true;
		if (hasMethod(type.getSuperclass(), name, descriptor, seen))
			return true;
		for (Class<?> parent : type.getInterfaces())
			if (hasMethod(parent, name, descriptor, seen))
				return true;
		return false;
	}

}
