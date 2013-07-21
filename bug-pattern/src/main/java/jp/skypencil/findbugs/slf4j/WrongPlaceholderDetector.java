package jp.skypencil.findbugs.slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.skypencil.findbugs.slf4j.parameter.AbstractDetectorForParameterArray;
import jp.skypencil.findbugs.slf4j.parameter.ArrayData;
import jp.skypencil.findbugs.slf4j.parameter.ThrowableHandler;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.Item;

public class WrongPlaceholderDetector extends AbstractDetectorForParameterArray {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern
            .compile("(.?)(\\\\\\\\)*\\{\\}");

    private final BugReporter bugReporter;

    private static final ImmutableSet<String> TARGET_METHOD_NAMES = ImmutableSet.of(
            "trace", "debug", "info", "warn", "error");

    // these methods do not use formatter
    private static final ImmutableSet<String> SIGS_WITHOUT_FORMAT = ImmutableSet.of(
            "(Ljava/lang/String;)V",
            "(Lorg/slf4j/Maker;Ljava/lang/String;)V",
            "(Ljava/lang/String;Ljava/lang/Throwable;)V",
            "(Lorg/slf4j/Maker;Ljava/lang/String;Ljava/lang/Throwable;)V");

    public WrongPlaceholderDetector(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void sawOpcode(int seen, ThrowableHandler throwableHandler) {
        if (seen == INVOKEINTERFACE) {
            checkLogger(throwableHandler);
        }
    }

    private void checkLogger(ThrowableHandler throwableHandler) {
        String signature = getSigConstantOperand();
        if (!Objects.equal("org/slf4j/Logger", getClassConstantOperand())
                || !TARGET_METHOD_NAMES.contains(getNameConstantOperand())) {
            return;
        }
        boolean withoutFormat = SIGS_WITHOUT_FORMAT.contains(signature);

        String formatString = getFormatString(stack, signature);
        if (formatString == null || withoutFormat) {
            return;
        }
        verifyFormat(formatString);

        int placeholderCount = countPlaceholder(formatString);
        int parameterCount;
        try {
            parameterCount = countParameter(stack, signature, throwableHandler);
        } catch (IllegalStateException e) {
            // Using unknown array as parameter. In this case, we cannot check number of parameter.
            BugInstance bug = new BugInstance(this,
                    "SLF4J_UNKNOWN_ARRAY", HIGH_PRIORITY)
                    .addSourceLine(this).addClassAndMethod(this)
                    .addCalledMethod(this);
            bugReporter.reportBug(bug);
            return;
        }

        if (placeholderCount != parameterCount) {
            BugInstance bug = new BugInstance(this,
                    "SLF4J_PLACE_HOLDER_MISMATCH", HIGH_PRIORITY)
                    .addInt(placeholderCount).addInt(parameterCount)
                    .addSourceLine(this).addClassAndMethod(this)
                    .addCalledMethod(this);
            bugReporter.reportBug(bug);
        }
    }

    private void verifyFormat(String formatString) {
        CodepointIterator iterator = new CodepointIterator(formatString);
        while (iterator.hasNext()) {
            if (Character.isLetter(iterator.next().intValue())) {
                // found non-sign character.
                return;
            }
        }

        BugInstance bug = new BugInstance(this,
                "SLF4J_SIGN_ONLY_FORMAT", NORMAL_PRIORITY)
                .addString(formatString)
                .addSourceLine(this).addClassAndMethod(this)
                .addCalledMethod(this);
        bugReporter.reportBug(bug);
    }

    int countParameter(OpcodeStack stack, String methodSignature, ThrowableHandler throwableHandler) {
        String[] signatures = splitSignature(methodSignature);
        if (Objects.equal(signatures[signatures.length - 1], "[Ljava/lang/Object;")) {
            ArrayData arrayData = (ArrayData) stack.getStackItem(0).getUserValue();
            if (arrayData == null || arrayData.getSize() < 0) {
                throw new IllegalStateException("no array initializer found");
            }
            int parameterCount = arrayData.getSize();
            if (arrayData.hasThrowableAtLast()) {
                --parameterCount;
            }
            return parameterCount;
        }

        int parameterCount = signatures.length - 1; // -1 means 'formatString' is not parameter
        if (Objects.equal(signatures[0], "Lorg/slf4j/Marker;")) {
            --parameterCount;
        }
        Item lastItem = stack.getStackItem(0);
        if (throwableHandler.checkThrowable(lastItem)) {
            --parameterCount;
        }
        return parameterCount;
    }

    int countPlaceholder(String format) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(format);
        int count = 0;
        while (matcher.find()) {
            if (!Objects.equal("\\", matcher.group(1))) {
                ++count;
            }
        }
        return count;
    }

    private String getFormatString(OpcodeStack stack, String signature) {
        // formatString is the first string in argument
        int stackIndex = indexOf(signature, "Ljava/lang/String;");
        Object constant = stack.getStackItem(stackIndex).getConstant();
        if (constant == null) {
            BugInstance bug = new BugInstance(this,
                    "SLF4J_FORMAT_SHOULD_BE_CONST", HIGH_PRIORITY)
                    .addSourceLine(this).addClassAndMethod(this)
                    .addCalledMethod(this);
            bugReporter.reportBug(bug);
            return null;
        }
        return constant.toString();
    }

    int indexOf(String methodSignature, String targetType) {
        String[] arguments = splitSignature(methodSignature);
        int index = arguments.length;

        for (String type : arguments) {
            --index;
            if (Objects.equal(type, targetType)) {
                return index;
            }
        }
        return -1;
    }

    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("^\\((.*)\\).*$");

    private String[] splitSignature(String methodSignature) {
        final Matcher matcher = SIGNATURE_PATTERN.matcher(methodSignature);
        if (matcher.find()) {
            String[] arguments = matcher.group(1).split(";");
            for (int i = 0; i < arguments.length; ++i) {
                arguments[i] = arguments[i] + ';';
            }
            return arguments;
        } else {
            throw new IllegalArgumentException();
        }
    }
}
