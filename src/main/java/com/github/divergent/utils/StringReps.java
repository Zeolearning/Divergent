package com.github.divergent.utils;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.resolution.types.ResolvedType;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.github.divergent.utils.Constant.*;
import static java.lang.String.format;

public class StringReps {
    public static String removeTail(String s, int offset) {
        return s.substring(0, Math.max(0, s.length() - offset));
    }

    public static String beforeChar(String s, char ch) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < s.length(); ++i) {
            if (s.charAt(i) == ch)
                break;
            builder.append(s.charAt(i));
        }
        return builder.toString();
    }

    public static List<String> splitToLines(String s) {
        return Arrays.stream(s.split(END_LINE_REGEX, -1)).toList();
    }

    public static boolean isValidPath(String path) {
        if (path.matches(".*[\\u4E00-\\u9FFF]+.*")) {
            return false;
        }
        return path.contains(MODULE_SRC_ROOT) || path.contains(MODULE_TEST_ROOT);
    }

    public static boolean isJavaFile(String path) {
        return path.endsWith(DOT_JAVA_EXT);
    }

    public static String objectToString(Object obj) {
        if (obj instanceof CallableDeclaration<?> m) {
            Node parent = m.getParentNode().orElse(null);
            return format("Method<%s: %s>", parent instanceof TypeDeclaration<?> cls ? cls.getName() : "", m.getSignature());
        }
        if (obj instanceof ClassOrInterfaceDeclaration cls) {
            return format("%s<%s>", cls.isInterface() ? "Interface" : "Class", cls.getNameAsString());
        }
        if (obj instanceof EnumDeclaration e) {
            return format("Enum<%s>", e.getNameAsString());
        }
        if (obj instanceof EnumConstantDeclaration entry) {
            return format("EnumEntry<%s>", entry);
        }
        if (obj instanceof FieldDeclaration f) {
            return format("Field<%s>", f.getVariables().stream().map(VariableDeclarator::getName).toList());
        }
        if (obj instanceof ResolvedType type) {
            return format("Type<%s>", type.describe());
        }
        if (obj instanceof VariableDeclarator var) {
            return format("Var<%s>", var.getName());
        }
        if (obj instanceof NodeWithSimpleName<?> n) {
            return n.getNameAsString();
        }
        return obj.getClass().toString();
    }

    public static boolean isComment(List<String> lines) {
        return lines.stream().allMatch(s -> s.isEmpty() || Arrays.stream(COMMENT_PREFIX).anyMatch(s::startsWith));
    }
}
