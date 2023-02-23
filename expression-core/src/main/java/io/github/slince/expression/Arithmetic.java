package io.github.slince.expression;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link <a href="https://github.com/apache/commons-jexl/blob/master/src/main/java/org/apache/commons/jexl3/JexlArithmetic.java">...</a>}
 */
public class Arithmetic {

    public static final Arithmetic INSTANCE = new Arithmetic(true);


    /** Marker class for null operand exceptions. */
    public static class NullOperand extends ArithmeticException {}

    /** Double.MAX_VALUE as BigDecimal. */
    protected static final BigDecimal BIGD_DOUBLE_MAX_VALUE = BigDecimal.valueOf(Double.MAX_VALUE);

    /** Double.MIN_VALUE as BigDecimal. */
    protected static final BigDecimal BIGD_DOUBLE_MIN_VALUE = BigDecimal.valueOf(Double.MIN_VALUE);

    /** Long.MAX_VALUE as BigInteger. */
    protected static final BigInteger BIGI_LONG_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);

    /** Long.MIN_VALUE as BigInteger. */
    protected static final BigInteger BIGI_LONG_MIN_VALUE = BigInteger.valueOf(Long.MIN_VALUE);

    /** Default BigDecimal scale. */
    protected static final int BIGD_SCALE = -1;

    /** Whether this JexlArithmetic instance behaves in strict or lenient mode. */
    private final boolean strict;

    /** The big decimal math context. */
    private final MathContext mathContext;

    /** The big decimal scale. */
    private final int mathScale;

    /**
     * Creates a JexlArithmetic.
     *
     * @param astrict whether this arithmetic is strict or lenient
     */
    public Arithmetic(boolean astrict) {
        this(astrict, null, Integer.MIN_VALUE);
    }

    /**
     * Creates a JexlArithmetic.
     *
     * @param astrict     whether this arithmetic is lenient or strict
     * @param bigdContext the math context instance to use for +,-,/,*,% operations on big decimals.
     * @param bigdScale   the scale used for big decimals.
     */
    public Arithmetic(boolean astrict, MathContext bigdContext, int bigdScale) {
        this.strict = astrict;
        this.mathContext = bigdContext == null ? MathContext.DECIMAL128 : bigdContext;
        this.mathScale = bigdScale == Integer.MIN_VALUE ? BIGD_SCALE : bigdScale;
    }

    /**
     * Checks whether this JexlArithmetic instance
     * strictly considers null as an error when used as operand unexpectedly.
     *
     * @return true if strict, false if lenient
     */
    public boolean isStrict() {
        return this.strict;
    }

    /**
     * The MathContext instance used for +,-,/,*,% operations on big decimals.
     *
     * @return the math context
     */
    public MathContext getMathContext() {
        return mathContext;
    }

    /**
     * The BigDecimal scale used for comparison and coericion operations.
     *
     * @return the scale
     */
    public int getMathScale() {
        return mathScale;
    }

    /**
     * Ensure a big decimal is rounded by this arithmetic scale and rounding mode.
     *
     * @param number the big decimal to round
     * @return the rounded big decimal
     */
    protected BigDecimal roundBigDecimal(final BigDecimal number) {
        int mscale = getMathScale();
        if (mscale >= 0) {
            return number.setScale(mscale, getMathContext().getRoundingMode());
        } else {
            return number;
        }
    }

    /**
     * The result of +,/,-,*,% when both operands are null.
     *
     * @return Integer(0) if lenient
     * @throws ArithmeticException if strict
     */
    protected Object controlNullNullOperands() {
        if (isStrict()) {
            throw new NullOperand();
        }
        return 0;
    }

    /**
     * Throw a NPE if arithmetic is strict.
     *
     * @throws ArithmeticException if strict
     */
    protected void controlNullOperand() {
        if (isStrict()) {
            throw new NullOperand();
        }
    }

    /**
     * The float regular expression pattern.
     * <p>
     * The decimal and exponent parts are optional and captured allowing to determine if the number is a real
     * by checking whether one of these 2 capturing groups is not empty.
     */
    public static final Pattern FLOAT_PATTERN = Pattern.compile("^[+-]?\\d*(\\.\\d*)?([eE][+-]?\\d+)?$");

    /**
     * Test if the passed value is a floating point number, i.e. a float, double
     * or string with ( "." | "E" | "e").
     *
     * @param val the object to be tested
     * @return true if it is, false otherwise.
     */
    protected boolean isFloatingPointNumber(Object val) {
        if (val instanceof Float || val instanceof Double) {
            return true;
        }
        if (val instanceof CharSequence) {
            final Matcher m = FLOAT_PATTERN.matcher((CharSequence) val);
            // first group is decimal, second is exponent;
            // one of them must exist hence start({1,2}) >= 0
            return m.matches() && (m.start(1) >= 0 || m.start(2) >= 0);
        }
        return false;
    }

    /**
     * Is Object a floating point number.
     *
     * @param o Object to be analyzed.
     * @return true if it is a Float or a Double.
     */
    protected boolean isFloatingPoint(final Object o) {
        return o instanceof Float || o instanceof Double;
    }

    /**
     * Is Object a whole number.
     *
     * @param o Object to be analyzed.
     * @return true if Integer, Long, Byte, Short or Character.
     */
    protected boolean isNumberable(final Object o) {
        return o instanceof Integer
                || o instanceof Long
                || o instanceof Byte
                || o instanceof Short
                || o instanceof Character;
    }

    /**
     * Given a Number, return back the value using the smallest type the result
     * will fit into.
     * <p>This works hand in hand with parameter 'widening' in java
     * method calls, e.g. a call to substring(int,int) with an int and a long
     * will fail, but a call to substring(int,int) with an int and a short will
     * succeed.</p>
     *
     * @param original the original number.
     * @return a value of the smallest type the original number will fit into.
     */
    public Number narrow(Number original) {
        return narrowNumber(original, null);
    }

    /**
     * Whether we consider the narrow class as a potential candidate for narrowing the source.
     *
     * @param narrow the target narrow class
     * @param source the orginal source class
     * @return true if attempt to narrow source to target is accepted
     */
    protected boolean narrowAccept(Class<?> narrow, Class<?> source) {
        return narrow == null || narrow.equals(source);
    }

    /**
     * Given a Number, return back the value attempting to narrow it to a target class.
     *
     * @param original the original number
     * @param narrow   the attempted target class
     * @return the narrowed number or the source if no narrowing was possible
     */
    public Number narrowNumber(Number original, Class<?> narrow) {
        if (original == null) {
            return null;
        }
        Number result = original;
        if (original instanceof BigDecimal) {
            BigDecimal bigd = (BigDecimal) original;
            // if it's bigger than a double it can't be narrowed
            if (bigd.compareTo(BIGD_DOUBLE_MAX_VALUE) > 0
                    || bigd.compareTo(BIGD_DOUBLE_MIN_VALUE) < 0) {
                return original;
            } else {
                try {
                    long l = bigd.longValueExact();
                    // coerce to int when possible (int being so often used in method parms)
                    if (narrowAccept(narrow, Integer.class)
                            && l <= Integer.MAX_VALUE
                            && l >= Integer.MIN_VALUE) {
                        return (int) l;
                    } else if (narrowAccept(narrow, Long.class)) {
                        return l;
                    }
                } catch (ArithmeticException xa) {
                    // ignore, no exact value possible
                }
            }
        }
        if (original instanceof Double || original instanceof Float) {
            double value = original.doubleValue();
            if (narrowAccept(narrow, Float.class)
                    && value <= Float.MAX_VALUE
                    && value >= Float.MIN_VALUE) {
                result = result.floatValue();
            }
            // else it fits in a double only
        } else {
            if (original instanceof BigInteger) {
                BigInteger bigi = (BigInteger) original;
                // if it's bigger than a Long it can't be narrowed
                if (bigi.compareTo(BIGI_LONG_MAX_VALUE) > 0
                        || bigi.compareTo(BIGI_LONG_MIN_VALUE) < 0) {
                    return original;
                }
            }
            long value = original.longValue();
            if (narrowAccept(narrow, Byte.class)
                    && value <= Byte.MAX_VALUE
                    && value >= Byte.MIN_VALUE) {
                // it will fit in a byte
                result = (byte) value;
            } else if (narrowAccept(narrow, Short.class)
                    && value <= Short.MAX_VALUE
                    && value >= Short.MIN_VALUE) {
                result = (short) value;
            } else if (narrowAccept(narrow, Integer.class)
                    && value <= Integer.MAX_VALUE
                    && value >= Integer.MIN_VALUE) {
                result = (int) value;
            }
            // else it fits in a long
        }
        return result;
    }

    /**
     * Given a BigInteger, narrow it to an Integer or Long if it fits and the arguments
     * class allow it.
     * <p>
     * The rules are:
     * if either arguments is a BigInteger, no narrowing will occur
     * if either arguments is a Long, no narrowing to Integer will occur
     * </p>
     *
     * @param lhs  the left hand side operand that lead to the bigi result
     * @param rhs  the right hand side operand that lead to the bigi result
     * @param bigi the BigInteger to narrow
     * @return an Integer or Long if narrowing is possible, the original BigInteger otherwise
     */
    protected Number narrowBigInteger(Object lhs, Object rhs, BigInteger bigi) {
        //coerce to long if possible
        if (!(lhs instanceof BigInteger || rhs instanceof BigInteger)
                && bigi.compareTo(BIGI_LONG_MAX_VALUE) <= 0
                && bigi.compareTo(BIGI_LONG_MIN_VALUE) >= 0) {
            // coerce to int if possible
            long l = bigi.longValue();
            // coerce to int when possible (int being so often used in method parms)
            if (!(lhs instanceof Long || rhs instanceof Long)
                    && l <= Integer.MAX_VALUE
                    && l >= Integer.MIN_VALUE) {
                return (int) l;
            }
            return l;
        }
        return bigi;
    }

    /**
     * Given a BigDecimal, attempt to narrow it to an Integer or Long if it fits if
     * one of the arguments is a numberable.
     *
     * @param lhs  the left hand side operand that lead to the bigd result
     * @param rhs  the right hand side operand that lead to the bigd result
     * @param bigd the BigDecimal to narrow
     * @return an Integer or Long if narrowing is possible, the original BigInteger otherwise
     */
    protected Number narrowBigDecimal(Object lhs, Object rhs, BigDecimal bigd) {
        if (isNumberable(lhs) || isNumberable(rhs)) {
            try {
                long l = bigd.longValueExact();
                // coerce to int when possible (int being so often used in method parms)
                if (l <= Integer.MAX_VALUE && l >= Integer.MIN_VALUE) {
                    return (int) l;
                } else {
                    return l;
                }
            } catch (ArithmeticException xa) {
                // ignore, no exact value possible
            }
        }
        return bigd;
    }

    /**
     * Replace all numbers in an arguments array with the smallest type that will fit.
     *
     * @param args the argument array
     * @return true if some arguments were narrowed and args array is modified,
     *         false if no narrowing occurred and args array has not been modified
     */
    public boolean narrowArguments(Object[] args) {
        boolean narrowed = false;
        for (int a = 0; a < args.length; ++a) {
            Object arg = args[a];
            if (arg instanceof Number) {
                Number narg = (Number) arg;
                Number narrow = narrow(narg);
                if (!narg.equals(narrow)) {
                    args[a] = narrow;
                    narrowed = true;
                }
            }
        }
        return narrowed;
    }

    /**
     * Add two values together.
     * <p>
     * If any numeric add fails on coercion to the appropriate type,
     * treat as Strings and do concatenation.
     * </p>
     *
     * @param left  left argument
     * @param right  right argument
     * @return left + right.
     */
    public Object add(Object left, Object right) {
        if (left == null && right == null) {
            return controlNullNullOperands();
        }
        boolean strconcat = strict
                            ? left instanceof String || right instanceof String
                            : left instanceof String && right instanceof String;
        if (!strconcat) {
            try {
                // if either are bigdecimal use that type
                if (left instanceof BigDecimal || right instanceof BigDecimal) {
                    BigDecimal l = toBigDecimal(left);
                    BigDecimal r = toBigDecimal(right);
                    BigDecimal result = l.add(r, getMathContext());
                    return narrowBigDecimal(left, right, result);
                }
                // if either are floating point (double or float) use double
                if (isFloatingPointNumber(left) || isFloatingPointNumber(right)) {
                    double l = toDouble(left);
                    double r = toDouble(right);
                    return l + r;
                }
                // otherwise treat as integers
                BigInteger l = toBigInteger(left);
                BigInteger r = toBigInteger(right);
                BigInteger result = l.add(r);
                return narrowBigInteger(left, right, result);
            } catch (NumberFormatException nfe) {
                if (left == null || right == null) {
                    controlNullOperand();
                }
            }
        }
        return toString(left).concat(toString(right));
    }

    /**
     * Divide the left value by the right.
     *
     * @param left  left argument
     * @param right  right argument
     * @return left / right
     * @throws ArithmeticException if right == 0
     */
    public Object divide(Object left, Object right) {
        if (left == null && right == null) {
            return controlNullNullOperands();
        }
        // if either are bigdecimal use that type
        if (left instanceof BigDecimal || right instanceof BigDecimal) {
            BigDecimal l = toBigDecimal(left);
            BigDecimal r = toBigDecimal(right);
            if (BigDecimal.ZERO.equals(r)) {
                throw new ArithmeticException("/");
            }
            BigDecimal result = l.divide(r, getMathContext());
            return narrowBigDecimal(left, right, result);
        }
        // if either are floating point (double or float) use double
        if (isFloatingPointNumber(left) || isFloatingPointNumber(right)) {
            double l = toDouble(left);
            double r = toDouble(right);
            if (r == 0.0) {
                throw new ArithmeticException("/");
            }
            return l / r;
        }
        // otherwise treat as integers
        BigInteger l = toBigInteger(left);
        BigInteger r = toBigInteger(right);
        if (BigInteger.ZERO.equals(r)) {
            throw new ArithmeticException("/");
        }
        BigInteger result = l.divide(r);
        return narrowBigInteger(left, right, result);
    }

    /**
     * left value modulo right.
     *
     * @param left  left argument
     * @param right  right argument
     * @return left % right
     * @throws ArithmeticException if right == 0.0
     */
    public Object mod(Object left, Object right) {
        if (left == null && right == null) {
            return controlNullNullOperands();
        }
        // if either are bigdecimal use that type
        if (left instanceof BigDecimal || right instanceof BigDecimal) {
            BigDecimal l = toBigDecimal(left);
            BigDecimal r = toBigDecimal(right);
            if (BigDecimal.ZERO.equals(r)) {
                throw new ArithmeticException("%");
            }
            BigDecimal remainder = l.remainder(r, getMathContext());
            return narrowBigDecimal(left, right, remainder);
        }
        // if either are floating point (double or float) use double
        if (isFloatingPointNumber(left) || isFloatingPointNumber(right)) {
            double l = toDouble(left);
            double r = toDouble(right);
            if (r == 0.0) {
                throw new ArithmeticException("%");
            }
            return l % r;
        }
        // otherwise treat as integers
        BigInteger l = toBigInteger(left);
        BigInteger r = toBigInteger(right);
        if (BigInteger.ZERO.equals(r)) {
            throw new ArithmeticException("%");
        }
        BigInteger result = l.mod(r);
        return narrowBigInteger(left, right, result);
    }

    /**
     * Multiply the left value by the right.
     *
     * @param left  left argument
     * @param right  right argument
     * @return left * right.
     */
    public Object multiply(Object left, Object right) {
        if (left == null && right == null) {
            return controlNullNullOperands();
        }
        // if either are bigdecimal use that type
        if (left instanceof BigDecimal || right instanceof BigDecimal) {
            BigDecimal l = toBigDecimal(left);
            BigDecimal r = toBigDecimal(right);
            BigDecimal result = l.multiply(r, getMathContext());
            return narrowBigDecimal(left, right, result);
        }
        // if either are floating point (double or float) use double
        if (isFloatingPointNumber(left) || isFloatingPointNumber(right)) {
            double l = toDouble(left);
            double r = toDouble(right);
            return l * r;
        }
        // otherwise treat as integers
        BigInteger l = toBigInteger(left);
        BigInteger r = toBigInteger(right);
        BigInteger result = l.multiply(r);
        return narrowBigInteger(left, right, result);
    }

    /**
     * Subtract the right value from the left.
     *
     * @param left  left argument
     * @param right  right argument
     * @return left - right.
     */
    public Object subtract(Object left, Object right) {
        if (left == null && right == null) {
            return controlNullNullOperands();
        }
        // if either are bigdecimal use that type
        if (left instanceof BigDecimal || right instanceof BigDecimal) {
            BigDecimal l = toBigDecimal(left);
            BigDecimal r = toBigDecimal(right);
            BigDecimal result = l.subtract(r, getMathContext());
            return narrowBigDecimal(left, right, result);
        }
        // if either are floating point (double or float) use double
        if (isFloatingPointNumber(left) || isFloatingPointNumber(right)) {
            double l = toDouble(left);
            double r = toDouble(right);
            return l - r;
        }
        // otherwise treat as integers
        BigInteger l = toBigInteger(left);
        BigInteger r = toBigInteger(right);
        BigInteger result = l.subtract(r);
        return narrowBigInteger(left, right, result);
    }

    /**
     * Negates a value (unary minus for numbers).
     *
     * @param val the value to negate
     * @return the negated value
     */
    public Object negate(Object val) {
        if (val instanceof Integer) {
            return -((Integer) val);
        } else if (val instanceof Double) {
            return - ((Double) val);
        } else if (val instanceof Long) {
            return -((Long) val);
        } else if (val instanceof BigDecimal) {
            return ((BigDecimal) val).negate();
        } else if (val instanceof BigInteger) {
            return ((BigInteger) val).negate();
        } else if (val instanceof Float) {
            return -((Float) val);
        } else if (val instanceof Short) {
            return (short) -((Short) val);
        } else if (val instanceof Byte) {
            return (byte) -((Byte) val);
        } else if (val instanceof Boolean) {
            return ((Boolean) val) ? Boolean.FALSE : Boolean.TRUE;
        } else if (val instanceof AtomicBoolean) {
            return ((AtomicBoolean) val).get() ? Boolean.FALSE : Boolean.TRUE;
        }
        throw new ArithmeticException("Object negation:(" + val + ")");
    }

    /**
     * Test if left contains right (right matches/in left).
     * <p>Beware that this method arguments are the opposite of the operator arguments.
     * 'x in y' means 'y contains x'.</p>
     *
     * @param container the container
     * @param value the value
     * @return test result or null if there is no arithmetic solution
     */
    public Boolean contains(Object container, Object value) {
        if (value == null && container == null) {
            //if both are null L == R
            return true;
        }
        if (value == null || container == null) {
            // we know both aren't null, therefore L != R
            return false;
        }
        // use arithmetic / pattern matching ?
        if (container instanceof Pattern) {
            return ((Pattern) container).matcher(value.toString()).matches();
        }
        if (container instanceof String) {
            return value.toString().matches(container.toString());
        }
        // try contains on map key
        if (container instanceof Map<?, ?>) {
            if (value instanceof Map<?, ?>) {
                return ((Map<?, ?>) container).keySet().containsAll(((Map<?, ?>) value).keySet());
            }
            return ((Map<?, ?>) container).containsKey(value);
        }
        // try contains on collection
        if (container instanceof Collection<?>) {
            if (value instanceof Collection<?>) {
                return ((Collection<?>) container).containsAll((Collection<?>) value);
            }
            // left in right ? <=> right.contains(left) ?
            return ((Collection<?>) container).contains(value);
        }
        return null;
    }

    /**
     * Test if left ends with right.
     *
     * @param left  left argument
     * @param right  right argument
     * @return left $= right if there is no arithmetic solution
     */
    public Boolean endsWith(Object left, Object right) {
        if (left == null && right == null) {
            //if both are null L == R
            return true;
        }
        if (left == null || right == null) {
            // we know both aren't null, therefore L != R
            return false;
        }
        if (left instanceof String) {
            return ((String) left).endsWith(toString(right));
        }
        return null;
    }

    /**
     * Test if left starts with right.
     *
     * @param left  left argument
     * @param right  right argument
     * @return left ^= right or null if there is no arithmetic solution
     */
    public Boolean startsWith(Object left, Object right) {
        if (left == null && right == null) {
            //if both are null L == R
            return true;
        }
        if (left == null || right == null) {
            // we know both aren't null, therefore L != R
            return false;
        }
        if (left instanceof String) {
            return ((String) left).startsWith(toString(right));
        }
        return null;
    }

    /**
     * Check for emptyness of various types: Number, Collection, Array, Map, String.
     *
     * @param object the object to check the emptyness of
     * @return the boolean or null of there is no arithmetic solution
     */
    public Boolean isEmpty(Object object) {
        if (object instanceof Number) {
            double d = ((Number) object).doubleValue();
            return Double.isNaN(d) || d == 0.d ? Boolean.TRUE : Boolean.FALSE;
        }
        if (object instanceof String) {
            return "".equals(object) ? Boolean.TRUE : Boolean.FALSE;
        }
        if (object.getClass().isArray()) {
            return Array.getLength(object) == 0 ? Boolean.TRUE : Boolean.FALSE;
        }
        if (object instanceof Collection<?>) {
            return ((Collection<?>) object).isEmpty() ? Boolean.TRUE : Boolean.FALSE;
        }
        // Map isn't a collection
        if (object instanceof Map<?, ?>) {
            return ((Map<?, ?>) object).isEmpty() ? Boolean.TRUE : Boolean.FALSE;
        }
        return null;
    }

    /**
     * Calculate the <code>size</code> of various types: Collection, Array, Map, String.
     *
     * @param object the object to get the size of
     * @return the size of object or null if there is no arithmetic solution
     */
    public Integer size(Object object) {
        if (object instanceof String) {
            return ((String) object).length();
        }
        if (object.getClass().isArray()) {
            return Array.getLength(object);
        }
        if (object instanceof Collection<?>) {
            return ((Collection<?>) object).size();
        }
        if (object instanceof Map<?, ?>) {
            return ((Map<?, ?>) object).size();
        }
        return null;
    }

    /**
     * Performs a bitwise and.
     *
     * @param left  the left operand
     * @param right the right operator
     * @return left &amp; right
     */
    public Object and(Object left, Object right) {
        long l = toLong(left);
        long r = toLong(right);
        return l & r;
    }

    /**
     * Performs a bitwise or.
     *
     * @param left  the left operand
     * @param right the right operator
     * @return left | right
     */
    public Object or(Object left, Object right) {
        long l = toLong(left);
        long r = toLong(right);
        return l | r;
    }

    /**
     * Performs a bitwise xor.
     *
     * @param left  the left operand
     * @param right the right operator
     * @return left ^ right
     */
    public Object xor(Object left, Object right) {
        long l = toLong(left);
        long r = toLong(right);
        return l ^ r;
    }

    /**
     * Performs a bitwise shl.
     *
     * @param left  the left operand
     * @param right the right operator
     * @return left << right
     */
    public Object shl(Object left, Object right) {
        long l = toLong(left);
        long r = toLong(right);
        return l << r;
    }

    /**
     * Performs a bitwise shr.
     *
     * @param left  the left operand
     * @param right the right operator
     * @return left >> right
     */
    public Object shr(Object left, Object right) {
        long l = toLong(left);
        long r = toLong(right);
        return l >> r;
    }

    /**
     * Performs a bitwise complement.
     *
     * @param val the operand
     * @return ~val
     */
    public Object not(Object val) {
        long l = toLong(val);
        return ~l;
    }

    /**
     * Performs a logical not.
     *
     * @param val the operand
     * @return !val
     */
    public Object logicNot(Object val) {
        return toBoolean(val) ? Boolean.FALSE : Boolean.TRUE;
    }

    /**
     * Performs a comparison.
     *
     * @param left     the left operand
     * @param right    the right operator
     * @param operator the operator
     * @return -1 if left &lt; right; +1 if left &gt; right; 0 if left == right
     * @throws ArithmeticException if either left or right is null
     */
    protected int compare(Object left, Object right, String operator) {
        if (left != null && right != null) {
            if (left instanceof BigDecimal || right instanceof BigDecimal) {
                BigDecimal l = toBigDecimal(left);
                BigDecimal r = toBigDecimal(right);
                return l.compareTo(r);
            } else if (left instanceof BigInteger || right instanceof BigInteger) {
                BigInteger l = toBigInteger(left);
                BigInteger r = toBigInteger(right);
                return l.compareTo(r);
            } else if (isFloatingPoint(left) || isFloatingPoint(right)) {
                double lhs = toDouble(left);
                double rhs = toDouble(right);
                if (Double.isNaN(lhs)) {
                    if (Double.isNaN(rhs)) {
                        return 0;
                    } else {
                        return -1;
                    }
                } else if (Double.isNaN(rhs)) {
                    // lhs is not NaN
                    return +1;
                } else if (lhs < rhs) {
                    return -1;
                } else if (lhs > rhs) {
                    return +1;
                } else {
                    return 0;
                }
            } else if (isNumberable(left) || isNumberable(right)) {
                long lhs = toLong(left);
                long rhs = toLong(right);
                if (lhs < rhs) {
                    return -1;
                } else if (lhs > rhs) {
                    return +1;
                } else {
                    return 0;
                }
            } else if (left instanceof String || right instanceof String) {
                return toString(left).compareTo(toString(right));
            } else if ("==".equals(operator)) {
                return left.equals(right) ? 0 : -1;
            } else if (left instanceof Comparable<?>) {
                @SuppressWarnings("unchecked") // OK because of instanceof check above
                final Comparable<Object> comparable = (Comparable<Object>) left;
                return comparable.compareTo(right);
            } else if (right instanceof Comparable<?>) {
                @SuppressWarnings("unchecked") // OK because of instanceof check above
                final Comparable<Object> comparable = (Comparable<Object>) right;
                return comparable.compareTo(left);
            }
        }
        throw new ArithmeticException("Object comparison:(" + left + " " + operator + " " + right + ")");
    }

    /**
     * Test if left and right are equal.
     *
     * @param left  left argument
     * @param right right argument
     * @return the test result
     */
    public boolean equals(Object left, Object right) {
        if (left == right) {
            return true;
        } else if (left == null || right == null) {
            return false;
        } else if (left instanceof Boolean || right instanceof Boolean) {
            return toBoolean(left) == toBoolean(right);
        } else {
            return compare(left, right, "==") == 0;
        }
    }

    /**
     * Test if left &lt; right.
     *
     * @param left  left argument
     * @param right right argument
     * @return the test result
     */
    public boolean lessThan(Object left, Object right) {
        if ((left == right) || (left == null) || (right == null)) {
            return false;
        } else {
            return compare(left, right, "<") < 0;
        }

    }

    /**
     * Test if left &gt; right.
     *
     * @param left  left argument
     * @param right right argument
     * @return the test result
     */
    public boolean greaterThan(Object left, Object right) {
        if ((left == right) || left == null || right == null) {
            return false;
        } else {
            return compare(left, right, ">") > 0;
        }
    }

    /**
     * Test if left &lt;= right.
     *
     * @param left  left argument
     * @param right right argument
     * @return the test result
     */
    public boolean lessThanOrEqual(Object left, Object right) {
        if (left == right) {
            return true;
        } else if (left == null || right == null) {
            return false;
        } else {
            return compare(left, right, "<=") <= 0;
        }
    }

    /**
     * Test if left &gt;= right.
     *
     * @param left  left argument
     * @param right right argument
     * @return the test result
     */
    public boolean greaterThanOrEqual(Object left, Object right) {
        if (left == right) {
            return true;
        } else if (left == null || right == null) {
            return false;
        } else {
            return compare(left, right, ">=") >= 0;
        }
    }

    /**
     * Coerce to a primitive boolean.
     * <p>Double.NaN, null, "false" and empty string coerce to false.</p>
     *
     * @param val value to coerce
     * @return the boolean value if coercion is possible, true if value was not null.
     */
    public boolean toBoolean(Object val) {
        if (val == null) {
            controlNullOperand();
            return false;
        } else if (val instanceof Boolean) {
            return ((Boolean) val);
        } else if (val instanceof Number) {
            double number = toDouble(val);
            return !Double.isNaN(number) && number != 0.d;
        } else if (val instanceof AtomicBoolean) {
            return ((AtomicBoolean) val).get();
        } else if (val instanceof String) {
            String strval = val.toString();
            return strval.length() > 0 && !"false".equals(strval);
        } else {
            // non null value is true
            return true;
        }
    }

    /**
     * Coerce to a primitive int.
     * <p>Double.NaN, null and empty string coerce to zero.</p>
     * <p>Boolean false is 0, true is 1.</p>
     *
     * @param val value to coerce
     * @return the value coerced to int
     * @throws ArithmeticException if val is null and mode is strict or if coercion is not possible
     */
    public int toInteger(Object val) {
        if (val == null) {
            controlNullOperand();
            return 0;
        } else if (val instanceof Double) {
            Double dval = (Double) val;
            if (Double.isNaN(dval)) {
                return 0;
            } else {
                return dval.intValue();
            }
        } else if (val instanceof Number) {
            return ((Number) val).intValue();
        } else if (val instanceof String) {
            if ("".equals(val)) {
                return 0;
            }
            return Integer.parseInt((String) val);
        } else if (val instanceof Boolean) {
            return ((Boolean) val) ? 1 : 0;
        } else if (val instanceof AtomicBoolean) {
            return ((AtomicBoolean) val).get() ? 1 : 0;
        } else if (val instanceof Character) {
            return ((Character) val);
        }

        throw new ArithmeticException("Integer coercion: "
                + val.getClass().getName() + ":(" + val + ")");
    }

    /**
     * Coerce to a primitive long.
     * <p>Double.NaN, null and empty string coerce to zero.</p>
     * <p>Boolean false is 0, true is 1.</p>
     *
     * @param val value to coerce
     * @return the value coerced to long
     * @throws ArithmeticException if value is null and mode is strict or if coercion is not possible
     */
    public long toLong(Object val) {
        if (val == null) {
            controlNullOperand();
            return 0L;
        } else if (val instanceof Double) {
            Double dval = (Double) val;
            if (Double.isNaN(dval)) {
                return 0L;
            } else {
                return dval.longValue();
            }
        } else if (val instanceof Number) {
            return ((Number) val).longValue();
        } else if (val instanceof String) {
            if ("".equals(val)) {
                return 0L;
            } else {
                return Long.parseLong((String) val);
            }
        } else if (val instanceof Boolean) {
            return ((Boolean) val) ? 1L : 0L;
        } else if (val instanceof AtomicBoolean) {
            return ((AtomicBoolean) val).get() ? 1L : 0L;
        } else if (val instanceof Character) {
            return ((Character) val);
        }

        throw new ArithmeticException("Long coercion: "
                + val.getClass().getName() + ":(" + val + ")");
    }

    /**
     * Coerce to a BigInteger.
     * <p>Double.NaN, null and empty string coerce to zero.</p>
     * <p>Boolean false is 0, true is 1.</p>
     *
     * @param val the object to be coerced.
     * @return a BigDecimal
     * @throws ArithmeticException if val is null and mode is strict or if coercion is not possible
     */
    public BigInteger toBigInteger(Object val) {
        if (val == null) {
            controlNullOperand();
            return BigInteger.ZERO;
        } else if (val instanceof BigInteger) {
            return (BigInteger) val;
        } else if (val instanceof Double) {
            Double dval = (Double) val;
            if (Double.isNaN(dval)) {
                return BigInteger.ZERO;
            } else {
                return BigInteger.valueOf(dval.longValue());
            }
        } else if (val instanceof BigDecimal) {
            return ((BigDecimal) val).toBigInteger();
        } else if (val instanceof Number) {
            return BigInteger.valueOf(((Number) val).longValue());
        } else if (val instanceof Boolean) {
            return BigInteger.valueOf(((Boolean) val) ? 1L : 0L);
        } else if (val instanceof AtomicBoolean) {
            return BigInteger.valueOf(((AtomicBoolean) val).get() ? 1L : 0L);
        } else if (val instanceof String) {
            String string = (String) val;
            if ("".equals(string)) {
                return BigInteger.ZERO;
            } else {
                return new BigInteger(string);
            }
        } else if (val instanceof Character) {
            int i = ((Character) val);
            return BigInteger.valueOf(i);
        }

        throw new ArithmeticException("BigInteger coercion: "
                + val.getClass().getName() + ":(" + val + ")");
    }

    /**
     * Coerce to a BigDecimal.
     * <p>Double.NaN, null and empty string coerce to zero.</p>
     * <p>Boolean false is 0, true is 1.</p>
     *
     * @param val the object to be coerced.
     * @return a BigDecimal.
     * @throws ArithmeticException if val is null and mode is strict or if coercion is not possible
     */
    public BigDecimal toBigDecimal(Object val) {
        if (val instanceof BigDecimal) {
            return roundBigDecimal((BigDecimal) val);
        } else if (val == null) {
            controlNullOperand();
            return BigDecimal.ZERO;
        } else if (val instanceof Double) {
            if (Double.isNaN(((Double) val))) {
                return BigDecimal.ZERO;
            } else {
                return roundBigDecimal(new BigDecimal(val.toString(), getMathContext()));
            }
        } else if (val instanceof Number) {
            return roundBigDecimal(new BigDecimal(val.toString(), getMathContext()));
        } else if (val instanceof Boolean) {
            return BigDecimal.valueOf(((Boolean) val) ? 1. : 0.);
        } else if (val instanceof AtomicBoolean) {
            return BigDecimal.valueOf(((AtomicBoolean) val).get() ? 1L : 0L);
        } else if (val instanceof String) {
            String string = (String) val;
            if ("".equals(string)) {
                return BigDecimal.ZERO;
            }
            return roundBigDecimal(new BigDecimal(string, getMathContext()));
        } else if (val instanceof Character) {
            int i = ((Character) val);
            return new BigDecimal(i);
        }
        throw new ArithmeticException("BigDecimal coercion: "
                + val.getClass().getName() + ":(" + val + ")");
    }

    /**
     * Coerce to a primitive double.
     * <p>Double.NaN, null and empty string coerce to zero.</p>
     * <p>Boolean false is 0, true is 1.</p>
     *
     * @param val value to coerce.
     * @return The double coerced value.
     * @throws ArithmeticException if val is null and mode is strict or if coercion is not possible
     */
    public double toDouble(Object val) {
        if (val == null) {
            controlNullOperand();
            return 0;
        } else if (val instanceof Double) {
            return ((Double) val);
        } else if (val instanceof Number) {
            //The below construct is used rather than ((Number)val).doubleValue() to ensure
            //equality between comparing new Double( 6.4 / 3 ) and the jexl expression of 6.4 / 3
            return Double.parseDouble(String.valueOf(val));
        } else if (val instanceof Boolean) {
            return ((Boolean) val) ? 1. : 0.;
        } else if (val instanceof AtomicBoolean) {
            return ((AtomicBoolean) val).get() ? 1. : 0.;
        } else if (val instanceof String) {
            String string = (String) val;
            if ("".equals(string)) {
                return Double.NaN;
            } else {
                // the spec seems to be iffy about this.  Going to give it a wack anyway
                return Double.parseDouble(string);
            }
        } else if (val instanceof Character) {
            int i = ((Character) val);
            return i;
        }
        throw new ArithmeticException("Double coercion: "
                + val.getClass().getName() + ":(" + val + ")");
    }

    /**
     * Coerce to a string.
     * <p>Double.NaN coerce to the empty string.</p>
     *
     * @param val value to coerce.
     * @return The String coerced value.
     * @throws ArithmeticException if val is null and mode is strict or if coercion is not possible
     */
    public String toString(Object val) {
        if (val == null) {
            controlNullOperand();
            return "";
        } else if (val instanceof Double) {
            Double dval = (Double) val;
            if (Double.isNaN(dval)) {
                return "";
            } else {
                return dval.toString();
            }
        } else {
            return val.toString();
        }
    }
}
