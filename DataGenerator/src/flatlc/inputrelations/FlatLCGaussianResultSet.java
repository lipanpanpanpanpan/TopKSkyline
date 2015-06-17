package flatlc.inputrelations;

import flatlc.levels.FlatLevelCombination;

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Random;


/**
 * <p>
 * This class represents a result set created of normal distributed random
 * numbers. As the seeds for the random number generation are fixed, the numbers
 * in a specific row will always be the same. This makes it possible to use a
 * large number of statistically independent "rows" without having to store them
 * on disc but with total reproducibility.
 * <p/>
 * The computed values will be wrapped into <code>FlatLevelCombination</code>
 * objects and used as level values. The minimum value therefore is 0 (zero),
 * the maximum value can be given in the constructor. The default maximum level
 * value is 9, leading to 10 different level values for each of the contained
 * values. According to the used normal distribution, the most frequent value
 * will be <code>(maximum level value) / 2</code> (with a variance of 1).
 * </p>
 * <p>
 * Each row is returned as an <code>FlatLevelCombination</code> object. The
 * preference object this level combination holds is an array of
 * <code>int</code> values. There is one column <code>id</code> representing a
 * unique id for each tuple. The <code>id</code>s are consecutive numbers. The
 * other column names are <code>colX</code>, where <code>X</code> is the number
 * of the column (starting at <code>0</code>).
 * </p>
 * <p>
 * Returning <code>FlatLevelCombination</code> object is one of the big
 * differences from <code>RandomResultSet</code>.
 * </p>
 * <p>
 * Additionally, this result set implementation is also capable of pre-sorting
 * the created random result objects.
 * </p>
 *
 * @author Timotheus Preisinger
 * @author endresma
 * @see RandomResultSet
 */
public class FlatLCGaussianResultSet extends FlatLCResultSetA {
    /**
     * default maxumum for column (=level) values
     */
    public static final int DEFAULT_MAX = 10;

    /**
     * Seed value for random data generator. Note that the same seed value
     * always leads to the same random numbers.
     */
    private int seed = 0;

    /**
     * counter containing the current row
     */
    int currentRow;
    int currentRowInMem;

    /**
     * number of tuples produced and thrown away at object initialization
     */
    int offset;

    /**
     * column value generator
     */
    Random generator;

    /**
     * meta data object
     */
    ResultSetMetaData meta;

    /**
     * maximum values for the column values
     */
    int[] maxValues;

    /**
     * multiplicators for the generation of unique ids
     */
    int[] multIDs;

    /**
     * in memory flag. Keeps the generated result set in memory. I.e. - peek is
     * supported - reset is supported on the same input tuples
     */
    boolean inMem = false;

    /**
     * set with objects that will be returned by <code>next()</code>
     */
    ArrayList<Object> elements;

    /**
     * Constructor. The column number is without counting the <code>id</code>
     * column. The maximum column value is set to <code>DEFAULT_MAX</code>.
     *
     * @param cols number of columns of random data
     * @param rows number of rows
     * @see FlatLCRandomResultSet.DEFAULT_MAX
     */
    public FlatLCGaussianResultSet(int cols, int rows) {
        this(cols, rows, DEFAULT_MAX, 0);
    }

    /**
     * Constructor. The column number is without counting the <code>id</code>
     * column. The minimum column value is set to 0 (zero), the maximum column
     * value is set to 100.
     *
     * @param cols   number of columns of random data
     * @param rows   number of rows.
     * @param offset number of rows thrown away before returning the first row
     */
    public FlatLCGaussianResultSet(int cols, int rows, int offset) {
        this(cols, rows, DEFAULT_MAX, offset);
    }

    /**
     * Constructor. The column number is without counting the <code>id</code>
     * column.
     *
     * @param cols    number of columns of random data
     * @param rows    number of rows
     * @param maximum maximum level value
     * @param offset  number of rows thrown away before returning the first row
     */
    public FlatLCGaussianResultSet(int cols, int rows, int maximum, int offset) {
        this.rows = rows;
        this.offset = offset;
        this.maxValues = new int[cols];
        for (int i = 0; i < maxValues.length; i++) {
            maxValues[i] = maximum;
        }
        init();
    }

    /**
     * Constructor
     *
     * @param rows      number of rows
     * @param maxValues maximum level values
     */
    public FlatLCGaussianResultSet(int rows, int[] maxValues) {
        this(rows, maxValues, 0);
    }

    /**
     * Constructor
     *
     * @param rows      number of rows
     * @param maxValues maximum level values
     * @param inMem     input distribution will be hold in main memory
     */
    public FlatLCGaussianResultSet(int rows, int[] maxValues, boolean inMem) {
        this.rows = rows;
        this.maxValues = maxValues;
        this.inMem = inMem;
        this.currentRowInMem = 0;
        init();
    }

    /**
     * Constructor
     *
     * @param rows      number of rows
     * @param maxValues maximum level values
     * @param offset    number of rows thrown away before returning the first row
     */
    public FlatLCGaussianResultSet(int rows, int[] maxValues, int offset) {
        this(rows, maxValues, offset, 0);

    }

    public FlatLCGaussianResultSet(int rows, int[] maxValues, int offset, int seed) {
        this.rows = rows;
        this.maxValues = maxValues;
        this.offset = offset;
        this.seed = seed;
        init();
    }

    /**
     * Initializes the random number generators.
     */
    private void init() {
        int len = this.maxValues.length;
        // construct new preference object
        // ParetoPreference preference = new ParetoPreference();
        // try {
        // for (int i = 0; i < len; i++) {
        // preference.append(new ExtremalPreference(null, false,
        // DefaultSVRelation.REGULAR, 1.0, 0.0, this.maxValues[i]));
        // }
        // } catch (PreferenceException e) {
        // e.printStackTrace();
        // }

        currentRow = 0;
        generator = new Random(seed);

        // compute the multiplicators
        this.multIDs = new int[len];
        this.multIDs[len - 1] = 1;
        for (int i = len; --i > 0; ) {
            this.multIDs[i - 1] = this.multIDs[i] * (maxValues[i] + 1);
        }

        // remove the offset
        for (int i = 0; i < offset; i++) {
            next();
        }

        if (inMem) {
            elements = new ArrayList<Object>();
            while (currentRowInMem++ < rows)
                elements.add(nextResult());
        }

        currentRowInMem = 0;

        // reset the row count
        currentRow = 0;
    }

    /**
     * Resets the row count. After calling this method, the number of rows to be
     * returned is reset. The row values are not identical to the already
     * returned objects. For a complete clone a new
     * <code>FlatLCRandomResultSet</code> object has to be created (using the
     * same parameters).
     */
    public void reset() {
        if (inMem)
            currentRowInMem = 0;
        else
            currentRow = 0;
    }

    public Object getMetaData() {
        if (meta == null) {
            meta = new RandomResultSetMetaData(maxValues);
        }
        return meta;
    }

    public boolean hasNext() throws IllegalStateException {
        if (inMem)
            return elements.size() > currentRowInMem;
        else
            return currentRow < rows;
    }

    public double[] nextVal() {
        double[] result = new double[maxValues.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (generator.nextGaussian() / 6) + 0.5;
            if (result[i] > 1.0 || result[i] < 0.0) {
                --i;
            }
        }

        return result;
    }

    private Object nextResult() throws IllegalStateException,
            NoSuchElementException {
        FlatLevelCombination result = null;
        if (currentRow++ < rows) {
            // generate a new row
            Object[] object = new Number[maxValues.length];
            int[] levels = new int[maxValues.length];
            double[] values = nextVal();
            // convert double values to integers
            for (int i = 0; i < levels.length; i++) {
                // int val = generator.nextInt(maxValues[i] + 1);
                int val = (int) (values[i] * (maxValues[i] + 1));
                object[i] = new Integer(val);
                levels[i] = val;
            }

            result = new FlatLevelCombination(levels, object, maxValues,
                    multIDs);

        }
        return result;
    }

    public Object next() throws IllegalStateException, NoSuchElementException {

        if (inMem) {
            return elements.get(currentRowInMem++);
        }

        return nextResult();

    }

    public Object peek() throws IllegalStateException, NoSuchElementException,
            UnsupportedOperationException {
        if (inMem) {
            return elements.get(currentRowInMem);
        } else
            throw new UnsupportedOperationException("peek is not supported");
    }

    public void open() {
    }

    public void close() {
    }

    public boolean supportsPeek() {
        if (inMem)
            return true;
        return false;
    }

    public boolean supportsRemove() {
        return false;
    }

    public boolean supportsReset() {
        return true;
    }

    public boolean supportsUpdate() {
        return false;
    }

    public void remove() throws IllegalStateException,
            UnsupportedOperationException {
        throw new UnsupportedOperationException("remove is not supported");
    }

    public void update(Object arg0) throws IllegalStateException,
            UnsupportedOperationException {
        throw new UnsupportedOperationException("update is not supported");
    }

    public ArrayList<Object> getElements() {
        return (ArrayList<Object>) elements.clone();

    }
}