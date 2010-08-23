/*
 *   This software is distributed under the terms of the FSF
 *   Gnu Lesser General Public License (see lgpl.txt).
 *
 *   This program is distributed WITHOUT ANY WARRANTY. See the
 *   GNU General Public License for more details.
 */
package com.scooterframework.orm.activerecord;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.scooterframework.common.exception.GenericException;
import com.scooterframework.common.exception.InvalidOperationException;
import com.scooterframework.common.logging.LogUtil;
import com.scooterframework.common.util.Converters;
import com.scooterframework.common.util.StringUtil;
import com.scooterframework.common.util.Util;
import com.scooterframework.common.util.WordUtil;
import com.scooterframework.common.validation.ValidationResults;
import com.scooterframework.orm.sqldataexpress.config.DatabaseConfig;
import com.scooterframework.orm.sqldataexpress.exception.BaseSQLException;
import com.scooterframework.orm.sqldataexpress.exception.UnexpectedDataException;
import com.scooterframework.orm.sqldataexpress.object.ColumnInfo;
import com.scooterframework.orm.sqldataexpress.object.OmniDTO;
import com.scooterframework.orm.sqldataexpress.object.RESTified;
import com.scooterframework.orm.sqldataexpress.object.RowData;
import com.scooterframework.orm.sqldataexpress.object.RowInfo;
import com.scooterframework.orm.sqldataexpress.object.TableInfo;
import com.scooterframework.orm.sqldataexpress.processor.DataProcessorTypes;
import com.scooterframework.orm.sqldataexpress.service.SqlService;
import com.scooterframework.orm.sqldataexpress.service.SqlServiceClient;
import com.scooterframework.orm.sqldataexpress.service.SqlServiceConfig;
import com.scooterframework.orm.sqldataexpress.util.DBStore;
import com.scooterframework.orm.sqldataexpress.util.SqlExpressUtil;
import com.scooterframework.transaction.ImplicitTransactionManager;
import com.scooterframework.transaction.TransactionManagerUtil;

/**
 * <p>
 * ActiveRecord represents a dynamic record in a particular database. Column
 * meta data of the table are retrieved before the ActiveRecord instance
 * is created. The following is an example for <tt>posts</tt> table.
 * </p>
 *
 * <pre>
 * public class Post extends ActiveRecord {
 * }
 * </pre>
 *
 * <p>
 * If not specified, the table name an ActiveRecord class maps to is the
 * plural form of the class name. And the database is the default database
 * connection defined in the <tt>database.properties</tt> file.
 * </p>
 *
 * <p>
 * To establish relations with other ActiveRecords, all subclasses must
 * implement registerRelations() method by calling proper relationship setup
 * methods: hasOne, belongsTo, hasMany, hasManyThrough, etc. For example:
 * </p>
 *
 * <pre>
 * public class Post extends ActiveRecord {
 *     public void registerRelations() {
 *         hasMany("comments");
 *     }
 * }
 * </pre>
 *
 * <p>
 * To modify an attribute of a record, you need to use one of the
 * <tt>setData</tt> methods. Otherwise, the change may not be saved.
 * </p>
 *
 *<h3>Specifying conditions</h3>
 *
 * <p>Conditions are often supplied in find, update and delete related methods.
 * They help to construct <tt>WHERE</tt> clause of a SQL statement. Conditions
 * can be provided in three ways:</p>
 * <ol>
 *   <li><tt>conditions</tt> map</li>
 *   <li><tt>conditionsSQL</tt> String</li>
 *   <li><tt>conditionsSQL</tt> String and <tt>conditionsSQLData</tt> map</li>
 * </ol>
 *
 * <p><tt>conditions</tt> is a map of column name and value pairs.
 * The key of <tt>conditions</tt> map is corresponding to a
 * column name in the RowInfo object. If the key is not a column name, its
 * value is ignored. This will generate a SQL where clause fragment based
 * on equality with the SQL AND operator. For example:</p>
 * <pre>
 * conditions map: {firstName=John, lastName=Doe}
 * translate to SQL: firstName=?firstName AND lastName=?lastName
 * </pre>
 *
 * <p>The key of <tt>conditions</tt> map is corresponding to a
 * column name in the RowInfo object. If the key is not a column name, its
 * value is ignored.</p>
 *
 * <p><tt>conditionsSQL</tt> String specifies a SQL fragment. For example:</p>
 * <pre>
 * conditionsSQL: "id in (1, 3, 5, 7) and content like '%Java%'"
 * </pre>
 *
 * <p><tt>conditionsSQL</tt> String and <tt>conditionsSQLData</tt> map allows
 * dynamic data in a SQL fragment. For example:</p>
 * <pre>
 * conditionsSQL: "first_name=?1 OR last_name=?2"
 * conditionsSQLData: 1=John, 2=Doe
 * </pre>
 *
 *<h3>Specifying options</h3>
 *
 * <p><tt>options</tt> can be either a string or a map.</p>
 *
 * <p>In an option string, each name-value pair is separated by ';'
 * character, while within each name-value pair, name and value strings
 * are separated by ':' character. For example, an option string like the
 * following: </p>
 * <blockquote><pre>
 *      conditions_sql: id in (1, 2, 3); include: category, user;
 *      order_by: first_name, salary DESC; cascade: delete
 * </pre></blockquote>
 *
 * is equivalent to a HashMap with the following entries:
 * <blockquote><pre>
 *      key                 value
 *      --------------      -----
 *      conditions_sql  =>  id in (1, 2, 3)
 *      include         =>  category, user
 *      order_by        =>  first_name, salary desc
 *      cascade         =>  delete
 * </pre></blockquote>
 *
 * <p>
 * Options string or map are used in finder methods such as find and findAll
 * related methods. Properties string or map are also used in association
 * methods such as <tt>hasMany</tt> and <tt>hasManyThrough</tt>. The
 * following is a list of allowed properties:
 * model, mapping, finder_sql, conditions_sql, include, join_type,
 * order_by, sort, order, unique, cascade. Please note that <tt>model</tt>
 * property is only used in setting up relations.
 * </p>
 *
 *<h3>Dynamic finders</h3>
 *
 * <p>Dynamic finders simulate Rails's finder methods. These methods are
 * <tt>findAllBy</tt>, <tt>findFirstBy</tt>, and <tt>findLastBy</tt>. The
 * first input parameter of these methods is <tt>columns</tt> which is a string
 * of column names linked by <tt>_and_</tt>, such as:
 * <pre>
 *     {columnName}_and_{columnName}_and_...
 * </pre>
 *
 * <p>A client can call this method as follows:</p>
 * <pre>
 *     employeeHome.findAllBy("firstName_and_lastName_and_age", {"John", "Doe", new Integer(29)});
 *     employeeHome.findAllBy("city", {"LA"});
 * </pre>
 *
 * <p>
 * Validators: see java doc of Validators class.
 * </p>
 *
 * @author (Fei) John Chen
 */
public class ActiveRecord implements RESTified, Serializable {

    /**
     * <p>Constructs an instance of ActiveRecord.</p>
     *
     * <p>This constructor will populate the meta info of the record and its
     * table. The table name defaults to short class name. For example, if
     * the model class name is <tt>com.example.models.User</tt>, the default
     * table name for this class is <tt>posts</tt> or <tt>CRM_users_US</tt>
     * if there are prefix <tt>CRM_</tt> and suffix <tt>_US</tt> for all
     * tables as specified in configuration file.</p>
     */
    public ActiveRecord() {
        connectionName = getDefaultConnectionName();
        tableName = getTableName();
        initialize(connectionName, tableName);
    }

	/**
     * <p>Constructs an instance of ActiveRecord.</p>
     *
     * <p>
     * This constructor will populate the meta info of the record and its
     * table. </p>
     *
     * <p>
     * <tt>tableName</tt> is table name of the record. The the prefix and
     * suffix of the database table name may be removed. For example, if a
     * table name is <tt>CRM_users_US</tt> which has a prefix
     * <tt>CRM_</tt> and a suffix <tt>_US</tt>, the slim table name
     * used here can just be <tt>users</tt>. </p>
     *
     * @param tableName table name of the record.
     */
    public ActiveRecord(String tableName) {
        if (tableName == null)
            throw new IllegalArgumentException("Table name cannot be null in ActiveRecord().");
        this.connectionName = getDefaultConnectionName();
        setTableName(tableName);
        initialize(connectionName, tableName);
    }

	/**
     * <p>Constructs an instance of ActiveRecord for a specific database
     * connection.</p>
     *
     * <p>
     * This constructor will populate the meta info of the record and its
     * table. </p>
     *
     * <p>
     * <tt>connectionName</tt> is database connection name. For example,
     * for the following entry in <tt>database.properties</tt> file, the
     * <tt>connectionName</tt> is <tt>jpetstore</tt>:</p>
     *
     * <pre>
		database.connection.jpetstore=\
				driver=oracle.jdbc.driver.OracleDriver,\
				url=jdbc:oracle:thin:@127.0.0.1:1521:jpetstore,\
				username=scott,\
				password=tiger
     * </pre>
     *
     * <p>
     * <tt>tableName</tt> is table name of the record. The the prefix and
     * suffix of the database table name may be removed. For example, if a
     * table name is <tt>CRM_users_US</tt> which has a prefix
     * <tt>CRM_</tt> and a suffix <tt>_US</tt>, the slim table name
     * used here can just be <tt>users</tt>. </p>
     *
     * @param connectionName database connection name.
     * @param tableName table name of the record.
     */
    public ActiveRecord(String connectionName, String tableName) {
        if (tableName == null)
            throw new IllegalArgumentException("Table name cannot be null in ActiveRecord().");
        this.connectionName = connectionName;
        setTableName(tableName);
        initialize(connectionName, tableName);
    }

    /**
     * Returns the database connection name associated with this record instance.
     *
     * @return database connection name
     */
    public String getConnectionName() {
        return connectionName;
    }

    /**
     * <p>
     * Returns the restified id of the resource.
     * </p>
     *
     * <p>By default, this method returns a string of the primary key value of
     * the record. If the primary key is a composite key, a separator
     * ({@link com.scooterframework.orm.sqldataexpress.config.DatabaseConfig#PRIMARY_KEY_SEPARATOR}) is used
     * to link values of the key fields. The order of the fields of a composite
     * primary key is defined by the {@link #getRestfulIdConfig getRestfulIdConfig} method.
     * </p>
     *
     * <p>If the underline data does not have primary key, then all columns of
     * the data are used to compute the <tt>id</tt>.</p>
     *
     * <p>Subclass may override this method if a customized string format is
     * required.</p>
     *
     * @return id String
     */
    public String getRestfulId() {
        return (rowData != null)?rowData.getRestfulId():null;
    }

    /**
     * Returns column names corresponding to the restified id value returned by
     * the {@link #getRestfulId getRestfulId} method. By default, the order of the field names
     * are obtained from the {@link #getPrimaryKeyNames getPrimaryKeyNames} method.
     * Subclass may override this method if a customized string array is required.
     *
     * @return string array of fields behind the id
     */
    public String[] getRestfulIdConfig() {
        String[] results = null;
        if (rowInfo != null) {
            results = rowInfo.getPrimaryKeyColumnNames();
            if (results == null) results = rowInfo.getColumnNames();
        }
        return results;
    }

    /**
     * Returns the data map for the restified id. By default, the keys in the
     * map are primary key column names in lower case.
     *
     * @return map of restified id data
     */
    public Map getRestfulIdMap() {
        return (rowData != null)?rowData.getRestfulIdMap():(new HashMap());
    }

    /**
     * <p>Sets the id value of the resource. The format of the id string must
     * follow the pattern of the corresponding id config. If the id is backed
     * by a composite primary key, a separator
     * ({@link com.scooterframework.orm.sqldataexpress.config.DatabaseConfig#PRIMARY_KEY_SEPARATOR})
     * must be used to link values of each primary key column.</p>
     *
     * <pre>
     * Examples:
     *   id string          id config array         description
     *   ---------          ---------------         -------
     *     0001             [id]                    an order with id 0001
     *     0001-99          [order_id, id]          an item with id 99 on the order with id 0001
     *
     * </pre>
     *
     * @param id
     */
    public void setRestfulId(String id) {
        if (id == null) throw new IllegalArgumentException("Input id is null.");

        String[] ids = Converters.convertStringToStringArray(id, DatabaseConfig.PRIMARY_KEY_SEPARATOR, false);
        String[] fields = getRestfulIdConfig();
        if (ids.length != fields.length)
            throw new IllegalArgumentException("Input id does not match id config.");

        int total = ids.length;
        for (int i = 0; i < total; i++) {
            setData(fields[i], ids[i]);
        }
    }

    /**
     * Returns record meta data
     *
     * @return  RowInfo object of a table row
     * @see     com.scooterframework.orm.sqldataexpress.object.RowInfo
     */
    public RowInfo getRowInfo() {
        return rowInfo;
    }

    /**
     * Returns plain data of a row in a table
     *
     * @return  an array of objects
     */
    public Object[] getFields() {
        return rowData.getFields();
    }

    /**
     * Returns plain data for a column index
     *
     * @param   index    column index like 0, 1, 2, ...
     * @return  a data object for the column
     */
    public Object getField(int index) {
        return rowData.getField(index);
    }

    /**
     * Returns plain data for a field
     *
     * @param   fieldName name of a model field
     * @return  a data object for the field
     */
    public Object getField(String fieldName) {
        verifyExistenceOfField(fieldName);
        Object data = null;
        if (isExtraField(fieldName)) {
            data = getExtraFieldData(fieldName);
        }
        else {
            data = rowData.getField(fieldName);
        }
        return data;
    }

    /**
     * Returns a map of column name and value pairs
     *
     * @param   columnNames names of a database table column
     * @return  a map of column name and value pairs
     */
    public Map getFields(List columnNames) {
        Map edMap = getExtraFieldData(columnNames);
        Map rdMap = rowData.getDataMap(columnNames);

        Map dMap = new HashMap();
        if (edMap != null && edMap.size() > 0) dMap.putAll(edMap);
        if (rdMap != null && rdMap.size() > 0) dMap.putAll(rdMap);
        return dMap;
    }

    /**
     * Returns table meta data
     *
     * @return  TableInfo object
     * @see     com.scooterframework.orm.sqldataexpress.object.TableInfo
     */
    public TableInfo getTableInfo() {
        return lookupAndRegister(connectionName, getTableName());
    }

    /**
     * Returns the table name corresponding to the class name
     *
     * @param   className    a full class name of a class
     * @return  table name
     */
    public String getTableNameByClassName(String className) {
        return DBStore.getInstance().getTableName(className);
    }

    /**
     * Sets table name
     *
     * If the input table name is different from the current table name,
     * reset the fields of this record instance.
     *
     * @param   table    table name specified
     */
    public void setTableName(String table) {
        if (isFreezed()) throw new InvalidOperationException(this, "setTableName", "freezed");

        if (table == null || table.equals(""))
            throw new IllegalArgumentException("Failed to setTableName(): " +
                                               "Input table name is empty.");

        //add table name convention
        table = DatabaseConfig.getInstance().getFullTableName(table);

        //compare with the current tableName
        //if different, do something
        String currentTableName = getTableName();
        if (!table.equalsIgnoreCase(currentTableName) || (rowInfo == null)) {
            TableInfo ti = lookupAndRegister(connectionName, table);

            // clean up existing data
            rowInfo = ti.getHeader();
            rowData = new RowData(rowInfo, null);
            latestDbRowData = new RowData(rowInfo, null);
            tableName = table;
        }
    }

    /**
     * Sets primary key columns for the record
     */
    public void setPrimaryKey(Set primaryKeyNames) {
        if (isFreezed()) throw new InvalidOperationException(this, "setPrimaryKey", "freezed");

        TableInfo ti = getTableInfo();
        if (ti == null || primaryKeyNames == null ||
            primaryKeyNames.size()==0) return;
        ti.getHeader().setPrimaryKeyColumns(primaryKeyNames);
    }

    /**
     * Sets a column to be readonly
     *
     * Sometimes columns like "entry_user, entry_dt, update_user, update_dt or xxx_count"
     * are readonly. They will be updated by other database mechanisms.
     */
    public void setReadOnlyColumn(String readOnlyColumnName) {
        TableInfo ti = getTableInfo();
        if (ti == null || readOnlyColumnName == null) return;
        ti.getHeader().setReadOnlyColumn(readOnlyColumnName);
    }

    /**
     * Sets read-only columns
     *
     * Sometimes columns like "entry_user, entry_dt, update_user, update_dt"
     * are readonly. They will be updated by other database mechanisms.
     */
    public void setReadOnlyColumns(Set readOnlyColumnNames) {
        TableInfo ti = getTableInfo();
        if (ti == null || readOnlyColumnNames == null ||
            readOnlyColumnNames.size() == 0) return;
        ti.getHeader().setReadOnlyColumns(readOnlyColumnNames);
    }

    /**
     * Checks whether a column is a readonly column.
     *
     * @param colName the column name to be checked.
     * @return true if the column is readonly
     */
    public boolean isReadOnlyColumn(String colName) {
        TableInfo ti = getTableInfo();
        if (ti == null || colName == null) return false;
        return ti.getHeader().isReadOnlyColumn(colName);
    }

    /**
     * Checks whether a column is a required column. Data for a required
     * column cannot be set to null.
     *
     * @param colName the column name to be checked.
     * @return true if the column is required.
     */
    public boolean isRequiredColumn(String colName) {
        TableInfo ti = getTableInfo();
        if (ti == null || colName == null) return false;
        return ti.getHeader().isRequiredColumn(colName);
    }

    /**
     * indicates if the current record has been modified and unsaved to database.
     *
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * indicates if the current record is a new record--not in database yet.
     *
     */
    public boolean isNewRecord() {
        return !existInDatabase;
    }

    /**
     * indicates if the current record is freezed
     *
     */
    public boolean isFreezed() {
        return freezed;
    }

    /**
     * freezes the current record
     *
     */
    public void freeze() {
        freezed = true;
    }

    /**
     * indicates if the current instance is a home instance.
     *
     */
    public boolean isHomeInstance() {
        return isHomeInstance;
    }

    /**
     * Sets this instance as a home instance.
     *
     */
    void setAsHomeInstance() {
        isHomeInstance = true;
    }

    /**
     * creates the record in database and Returns it
     *
     */
    public ActiveRecord create() {
        ImplicitTransactionManager tm = TransactionManagerUtil.getImplicitTransactionManager();
        ActiveRecord r = null;

        try {
            tm.beginTransactionImplicit();

            beforeCreate();
            r = internal_create();
            afterCreate();

            tm.commitTransactionImplicit();
        }
        catch(BaseSQLException bdex) {
            tm.rollbackTransactionImplicit();
            throw bdex;
        }
        finally {
            tm.releaseResourcesImplicit();
        }

        return r;
    }

    /**
     * <p>Finds the record based on the current primary key values.</p>
     *
     * <p>If there is no primary key defined for the model, data from all
     * columns will be used as retrieving conditions.</p>
     */
    private void find() {
        if (isFreezed()) throw new InvalidOperationException(this, "find", "freezed");

        if (rowData == null) return;

        //populate a Map with primary key values
        RowInfo ri = getTableInfo().getHeader();
        String[] pkNames = ri.getPrimaryKeyColumnNames();
        if (pkNames == null || pkNames.length == 0) pkNames = ri.getColumnNames();

        int size = pkNames.length;
        Map inputs = new HashMap();
        for (int i=0; i< size; i++) {
            String columnName = pkNames[i];
            Object columnData = rowData.getField(columnName);
            inputs.put(columnName, columnData);
        }

        List list = ActiveRecordUtil.getGateway(getClass()).findAll(inputs);

        if (list != null && list.size()>0) {
            if (list.size() > 1) throw new UnexpectedDataException("retrieved " + list.size() + " records.");
            ActiveRecord refreshedRecord = (ActiveRecord)list.get(0);
            this.rowData = refreshedRecord.rowData;
        }
    }

    /**
     * <p>Updates the record based on its primary key values.</p>
     *
     * <p>If there is no primary key defined for the model, data from all
     * columns will be used as update conditions.</p>
     *
     */
    public void update() {
        if (isFreezed()) throw new InvalidOperationException(this, "update", "freezed");

        if (rowData == null) return;

        ImplicitTransactionManager tm = TransactionManagerUtil.getImplicitTransactionManager();

        try {
            tm.beginTransactionImplicit();

            beforeUpdate();

            Map conditions = null;
            String[] pkNames = rowInfo.getPrimaryKeyColumnNames();
            if (pkNames == null || pkNames.length == 0) {
                conditions = rowData.getDataMap();
            }
            else {
                conditions = rowData.getPrimaryKeyDataMap();
            }

            int updateCount = internal_update(conditions);

            if (updateCount > 1) throw new UnexpectedDataException("Should only update one, but actually updated " + updateCount + " records.");

            afterUpdate();

            tm.commitTransactionImplicit();
        }
        catch(BaseSQLException bdex) {
            tm.rollbackTransactionImplicit();
            throw bdex;
        }
        finally {
            tm.releaseResourcesImplicit();
        }
    }


    /**
     * Updates the record that satisfies the condition.
     *
     * @param Map conditions A Map of column name and value pairs.
     *
     * @return count of records updated.
     */
    private int internal_update(Map conditions) {
        int count = 0;
        String updateSQL = "UPDATE " + getTableName();

        before_internal_update();

        try {
            Map inputs = new HashMap();

            int position = 1;

            //construct sets
            StringBuffer sets = new StringBuffer();
            position = prepareSetSQL(position, rowData, inputs, sets);
            updateSQL += " SET " + sets.toString();

            //construct where clause
            if (conditions != null && conditions.size() > 0) {
                StringBuffer wheres = new StringBuffer();
                position = prepareWhereClause(position, conditions, inputs, wheres);
                updateSQL += " WHERE " + wheres.toString();
            }

            log.debug("updates sql = " + updateSQL);

            OmniDTO returnTO =
                getSqlService().execute(inputs, DataProcessorTypes.DIRECT_SQL_STATEMENT_PROCESSOR, updateSQL);

            count = returnTO.getUpdatedRowCount();

            after_internal_update();
        }
        catch (Exception ex) {
            throw new BaseSQLException(ex);
        }

        return count;
    }

    /**
     * Updates a single field and saves the record.
     *
     * @param field a field name
     * @param value value of the field
     */
    public void updateField(String field, Object value) {
        if (isFreezed()) throw new InvalidOperationException(this, "updateField", "freezed");

        Map m = new HashMap();
        m.put(field, value);
        updateFields(m);
    }

    /**
     * Updates all the fields contained in the map for a record (row) in database
     *
     * @param fieldData a map of field and its data pairs
     */
    public void updateFields(Map fieldData) {
        if (isFreezed()) throw new InvalidOperationException(this, "updateFields", "freezed");

        setData(fieldData);
        updateChanged();
    }

    /**
     * Updates counters
     *
     * Counters map contains the names of the fields to update as keys and
     * the amount to update the field by as values.
     *
     * @param counters
     */
    public void updateCounters(Map counters) {
        updateFields(counters);
    }

    /**
     * Increments counter field by 1.
     *
     * @param btr BelongsToRecordRelation
     */
    public void incrementCounter(BelongsToRecordRelation btr) {
        incrementCounter(((BelongsToRelation)btr.getRelation()).getCounterCacheName());
    }

    /**
     * Increments counter field by 1.
     *
     * @param counterFieldName
     */
    public void incrementCounter(String counterFieldName) {
        incrementCounter(counterFieldName, +1);
    }

    /**
     * Increments counter field by amount.
     *
     * @param btr BelongsToRecordRelation
     * @param amount
     */
    public void incrementCounter(BelongsToRecordRelation btr, int amount) {
        incrementCounter(((BelongsToRelation)btr.getRelation()).getCounterCacheName(), amount);
    }

    /**
     * Increments counter field by amount.
     *
     * @param counterFieldName
     * @param amount
     */
    public void incrementCounter(String counterFieldName, int amount) {
        Object oldCount = getField(counterFieldName);
        int oldIntValue = Util.getSafeIntValue(oldCount);
        Map counters = new HashMap();
        counters.put(counterFieldName, new Integer(oldIntValue + amount));
        updateCounters(counters);
    }

    /**
     * Decrements counter field by 1.
     *
     * @param btr BelongsToRecordRelation
     */
    public void decrementCounter(BelongsToRecordRelation btr) {
        decrementCounter(((BelongsToRelation)btr.getRelation()).getCounterCacheName());
    }

    /**
     * Decrements counter field by 1.
     *
     * @param counterFieldName
     */
    public void decrementCounter(String counterFieldName) {
        decrementCounter(counterFieldName, +1);
    }

    /**
     * Decrements counter field by 1.
     *
     * @param btr BelongsToRecordRelation
     * @param amount
     */
    public void decrementCounter(BelongsToRecordRelation btr, int amount) {
        decrementCounter(((BelongsToRelation)btr.getRelation()).getCounterCacheName(), amount);
    }

    /**
     * Decrements counter field by amount.
     *
     * @param counterFieldName
     * @param amount
     */
    public void decrementCounter(String counterFieldName, int amount) {
        Object oldCount = getField(counterFieldName);
        int oldIntValue = Util.getSafeIntValue(oldCount);
        Map counters = new HashMap();
        counters.put(counterFieldName, new Integer(oldIntValue - amount));
        updateCounters(counters);
    }

    /**
     * Updates changed fields of a record (row) in database.
     *
     * @return number of records updated
     */
    public int updateChanged() {
        if (isFreezed()) throw new InvalidOperationException(this, "updateChanged", "freezed");

        ImplicitTransactionManager tm = TransactionManagerUtil.getImplicitTransactionManager();
        int updateCount = -1;

        try {
            tm.beginTransactionImplicit();

            beforeUpdateChanged();

            updateCount = internal_updateChanged();

            afterUpdateChanged();

            tm.commitTransactionImplicit();
        }
        catch(BaseSQLException bdex) {
            tm.rollbackTransactionImplicit();
            throw bdex;
        }
        finally {
            tm.releaseResourcesImplicit();
        }

        return updateCount;
    }

    /**
     * <p>Deletes the record based on the current primary key values.</p>
     *
     * <p>If there is no primary key defined for the model, data from all
     * columns will be used as delete conditions.</p>
     *
     * @return int number of records deleted
     */
    public int delete() {
        if (isFreezed() || isNewRecord()) return -1;

        ImplicitTransactionManager tm = TransactionManagerUtil.getImplicitTransactionManager();
        int count = -1;

        try {
            tm.beginTransactionImplicit();

            beforeDelete();

            before_internal_delete();

            //populate a Map with primary key values
            RowInfo ri = getTableInfo().getHeader();
            String[] pkNames = ri.getPrimaryKeyColumnNames();
            if (pkNames == null || pkNames.length == 0) pkNames = ri.getColumnNames();

            int size = pkNames.length;
            Map inputs = new HashMap();
            for (int i=0; i< size; i++) {
                String columnName = pkNames[i];
                Object columnData = rowData.getField(columnName);
                inputs.put(columnName, columnData);
            }

            count = ActiveRecordUtil.getGateway(getClass()).deleteAll(inputs);

            after_internal_delete();

            afterDelete();

            freeze();

            tm.commitTransactionImplicit();
        }
        catch(BaseSQLException bdex) {
            tm.rollbackTransactionImplicit();
            throw bdex;
        }
        finally {
            tm.releaseResourcesImplicit();
        }

        return count;
    }

    /**
     * reload the current record based on its primary key values.
     *
     */
    public void reload() {
        if (isFreezed()) throw new InvalidOperationException(this, "reload", "freezed");

        beforeFind();

        find();

        afterFind();
    }

    /**
     * saves the current record.
     *
     * If the record exists , use <tt>update()</tt>; otherwise
     * <tt>create()</tt>.
     */
    public void save() {
        if (isFreezed()) throw new InvalidOperationException(this, "save", "freezed");

        beforeSave();

        if (isNewRecord()) create();
        else update();

        afterSave();
    }

    /**
     * saves the current record and then reloads from database.
     *
     * If the record exists , use update(); otherwise create().
     *
     */
    public void saveAndReload() {
        if (isFreezed()) throw new InvalidOperationException(this, "SaveAndReload", "freezed");
        save();
        reload();
    }

    /**
     * Sets column values by parsing input string nameValuePairs
     *
     * String nameValuePairs has the following format for example:
     *          firstName=John, lastName=Doe, age=10,...
     *       or firstName=John|lastName=Doe|age=10|...
     *       or firstName=John&lastName=Doe&age=10&...
     * You can use either ',', or '|' or '&' to separate each condition.
     *
     * The name of the data entry in the nameValuePairs is corresponding to a
     * column name in the RowInfo object. If the name is not a column name,
     * its value is ignored. If the column name is not in the names of
     * the nameValuePairs, the column data is set to null.
     *
     * If a column name is protected, its data is unaffected. Use setData
     * method to set its data.
     */
    public void clearAndSetData(String nameValuePairs) {
        clearAndSetData(Converters.convertStringToMap(nameValuePairs));
    }

    /**
     * Sets column values by parsing input string nameValuePairs
     * from a Map
     *
     * The key of the data entry in the Map is corresponding to a
     * column name in the RowInfo object. If the key is not a column name,
     * its value is ignored. If the column name is not in the key set of
     * the Map, the column data is set to null.
     *
     * If a column name is protected, its data is unaffected. Use setData
     * method to set its data.
     */
    public void clearAndSetData(Map inputDataMap) {
        if (isFreezed()) throw new InvalidOperationException(this, "clearAndSetData", "freezed");

        //filter out protected fields before setting data
        rowData.clearAndSetData(filterProtectedFields(inputDataMap));
    }

    /**
     * Sets column values by parsing input string nameValuePairs
     *
     * String nameValuePairs has the following format for example:
     *          firstName=John, lastName=Doe, age=10,...
     *       or firstName=John|lastName=Doe|age=10|...
     *       or firstName=John&lastName=Doe&age=10&...
     * You can use either ',', or '|' or '&' to separate each condition.
     *
     * The name of the data entry in the nameValuePairs is corresponding to a
     * column name in the RowInfo object. If the name is not a column name, its
     * value is ignored. If the column name is not in the names of the
     * nameValuePairs, the column data is not updated. To set those column data
     * to null when the column name is not in the key set, use the
     * {@link #clearAndSetData(java.lang.String) clearAndSetData} method.
     *
     * This method does not save data to database. Use save() or create() or
     * update() or updateChanged() to save data to database.
     *
     * If a column name is protected, its data is unaffected. Use setData
     * method to set its data.
     */
    public void setData(String nameValuePairs) {
        if (isFreezed()) throw new InvalidOperationException(this, "setData", "freezed");

        if (nameValuePairs == null || "".equals(nameValuePairs.trim())) return;

        setData(Converters.convertStringToMap(nameValuePairs));
    }

    /**
     * Sets column values by parsing input string nameValuePairs from a Map.
     *
     * The key of the data entry in the Map is corresponding to a
     * column name in the RowInfo object. If the key is not a column name, its
     * value is ignored. If the column name is not in the key set of the
     * Map, or the column is readonly or not writable, or is primary key of an
     * existing record, the column data is not updated.
     *
     * To set those column data to null when the column name is not in the key
     * set, use the
     * {@link #clearAndSetData(java.util.Map) clearAndSetData} method.
     *
     * This method does not save data to database. Use save() or create() or
     * update() to save data to database.
     *
     * If a column name is protected, its data is unaffected.
     */
    public void setData(Map inputDataMap) {
        if (isFreezed()) throw new InvalidOperationException(this, "setData", "freezed");

        if (inputDataMap == null || inputDataMap.size() == 0) return;

        beforeSetData();

        Map data = new HashMap(inputDataMap.size());
        Iterator it = inputDataMap.keySet().iterator();
        while(it.hasNext()) {
            String key = (String)it.next();
            if (rowInfo.isPrimaryKeyColumn(key) && existInDatabase) continue;
            data.put(key, inputDataMap.get(key));
        }

        List modifiedFieldNames = setExtraFieldData(data);

        //filter out protected fields before setting data
        List modifiedColumnNames = rowData.setData(filterProtectedFields(data));

        List modifiedNames = new ArrayList();
        if (modifiedFieldNames != null && modifiedFieldNames.size() > 0)
            modifiedNames.addAll(modifiedFieldNames);
        if (modifiedColumnNames != null && modifiedColumnNames.size() > 0)
            modifiedNames.addAll(modifiedColumnNames);

        afterSetData(modifiedNames);
    }

    /**
     * Sets column data for a column index
     *
     * index: 0, 1, 2, ...
     *
     * This method does not save data to database. Use save() or create() or
     * update() to save data to database.
     */
    public void setData(int index, Object columnData) {
        if (isFreezed()) throw new InvalidOperationException(this, "setData", "freezed");
        beforeSetData();
        rowData.setField(index, columnData);
        afterSetData(index);
    }

    /**
     * Sets data for a field
     *
     * If there is no such a field, an InvalidColumnNameException
     * will be thrown.
     *
     * This method does not save data to database. Use save() or create() or
     * update() to save data to database.
     */
    public void setData(String fieldName, Object fieldData) {
        if (isFreezed()) throw new InvalidOperationException(this, "setData", "freezed");
        beforeSetData();

        if (isExtraField(fieldName)) {
            setExtraFieldData(fieldName, fieldData);
        }
        else if (!isProtectedField(fieldName)) {
            rowData.setField(fieldName, fieldData);
        }

        afterSetData(fieldName);
    }

    /**
     * Returns all database column names of the record
     *
     * @return String all fields as a comma delimited string
     */
    public String getAllFieldNames() {
        String[] allColumnNames = rowInfo.getColumnNames();
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<allColumnNames.length; i++) {
            sb.append(allColumnNames[i]).append(", ");
        }

        String allnames = allColumnNames.toString();

        //remove the last ", "
        if (allnames.endsWith(", ")) {
            allnames = allnames.substring(0, allnames.lastIndexOf(','));
        }

        return allnames;
    }

    /**
     * Checks if a data map contains primary key
     *
     * @param data Map of input data
     * @return boolean state indicates if the data map contains primary field(s)
     */
    public boolean containsPrimaryKey(Map data) {
        boolean state = false;
        if (data != null && data.size() > 0) {
            Iterator it = data.keySet().iterator();
            while(it.hasNext()) {
                String key = (String)it.next();
                if (rowInfo.isPrimaryKeyColumn(key)) {
                    state = true;
                    break;
                }
            }
        }

        return state;
    }

    /**
     * Returns a string array of primary key names.
     *
     * @return String[]
     */
    public String[] getPrimaryKeyNames() {
        return rowInfo.getPrimaryKeyColumnNames();
    }

    /**
     * Returns the data map for primary keys. The keys in the map are primary
     * key column names in lower case. An empty map is returned if the underline
     * data is not retrieved or the record is new.
     *
     * @return map of primary key data
     */
    public Map getPrimaryKeyDataMap() {
        return (rowData != null)?rowData.getPrimaryKeyDataMap():new HashMap();
    }

    /**
     * Returns an AssociatedRecord instance of a specific class type.
     *
     * @param target class of the associated record
     * @return the AssociatedRecord instance
     */
    public AssociatedRecord associated(Class target) {
        return associated(target, false);
    }

    /**
     * Returns an AssociatedRecord instance of a specific class type.
     *
     * @param target class of the associated record
     * @param refresh true if reload database data
     * @return the AssociatedRecord instance
     */
    public AssociatedRecord associated(Class target, boolean refresh) {
        return associated(target, null, refresh);
    }

    /**
     * Returns an AssociatedRecord instance of a specific class type.
     *
     * <p>See top of this class for <tt>options</tt> examples.</p>
     *
     * @param target class of the associated record
     * @param options A string of options.
     * @return the AssociatedRecord instance
     */
    public AssociatedRecord associated(Class target, String options) {
        return associated(target, options, false);
    }

    /**
     * Returns an AssociatedRecord instance of a specific class type.
     *
     * <p>See top of this class for <tt>options</tt> examples.</p>
     *
     * @param target class of the associated record
     * @param options A string of options.
     * @param refresh true if reload database data
     * @return the AssociatedRecord instance
     */
    public AssociatedRecord associated(Class target, String options, boolean refresh) {
        return associated(ActiveRecordUtil.getModelName(target), options, refresh);
    }

    /**
     * Returns an AssociatedRecord instance.
     *
     * The <tt>associationId</tt> is the name of the <tt>belongs-to</tt> or
     * <tt>has-one</tt> relation defined in the class.
     *
     * @param associationId association id
     * @return the AssociatedRecord instance
     */
    public AssociatedRecord associated(String associationId) {
        return associated(associationId, false);
    }

    /**
     * Returns an AssociatedRecord instance.
     *
     * The <tt>associationId</tt> is the name of the <tt>belongs-to</tt> or
     * <tt>has-one</tt> relation defined in the class.
     *
     * @param associationId association id
     * @param refresh true if reload database data
     * @return the AssociatedRecord instance
     */
    public AssociatedRecord associated(String associationId, boolean refresh) {
        return associated(associationId, null, refresh);
    }

    /**
     * Returns an AssociatedRecord instance.
     *
     * The <tt>associationId</tt> is the name of the <tt>belongs-to</tt> or
     * <tt>has-one</tt> relation defined in the class.
     *
     * <p>See top of this class for <tt>options</tt> examples.</p>
     *
     * @param associationId association id
     * @param options A string of options.
     * @return the AssociatedRecord instance
     */
    public AssociatedRecord associated(String associationId, String options) {
        return associated(associationId, options, false);
    }

    /**
     * Returns an AssociatedRecord instance.
     *
     * The <tt>associationId</tt> is the name of the <tt>belongs-to</tt> or
     * <tt>has-one</tt> relation defined in the class.
     *
     * <p>See top of this class for <tt>options</tt> examples.</p>
     *
     * @param associationId association id
     * @param options A string of options.
     * @param refresh true if reload database data
     * @return the AssociatedRecord instance
     */
    public AssociatedRecord associated(String associationId, String options, boolean refresh) {
        return getRecordRelation(associationId).associatedRecord(options, refresh);
    }

    /**
     * Returns an AssociatedRecords instance of a specific class type.
     *
     * @param target class of the associated records
     * @return the AssociatedRecords instance
     */
    public AssociatedRecords allAssociated(Class target) {
        return allAssociated(target, false);
    }

    /**
     * Returns an AssociatedRecords instance of a specific class type.
     *
     * @param target class of the associated records
     * @param refresh true if reload database data
     * @return the AssociatedRecords instance
     */
    public AssociatedRecords allAssociated(Class target, boolean refresh) {
        return allAssociated(target, null, refresh);
    }

    /**
     * Returns an AssociatedRecords instance of a specific class type.
     *
     * <p>See top of this class for <tt>options</tt> examples.</p>
     *
     * @param target class of the associated records
     * @param options A string of options.
     * @return the AssociatedRecords instance
     */
    public AssociatedRecords allAssociated(Class target, String options) {
        return allAssociated(target, null, false);
    }

    /**
     * Returns an AssociatedRecords instance of a specific class type.
     *
     * @param target class of the associated records
     * @param options A string of options.
     * @param refresh true if reload database data
     * @return the AssociatedRecords instance
     */
    public AssociatedRecords allAssociated(Class target, String options, boolean refresh) {
        String model = ActiveRecordUtil.getModelName(target);
        return allAssociated(WordUtil.pluralize(model), options, refresh);
    }

    /**
     * Returns an AssociatedRecords instance.
     *
     * The <tt>associationId</tt> is the name of the <tt>has-many</tt> or
     * <tt>has-many-through</tt> relation defined in the class.
     *
     * @param associationId association id
     * @return the AssociatedRecords instance
     */
    public AssociatedRecords allAssociated(String associationId) {
        return allAssociated(associationId, false);
    }

    /**
     * Returns an AssociatedRecords instance.
     *
     * The <tt>associationId</tt> is the name of the <tt>has-many</tt> or
     * <tt>has-many-through</tt> relation defined in the class.
     *
     * @param associationId association id
     * @param refresh true if reload database data
     * @return the AssociatedRecords instance
     */
    public AssociatedRecords allAssociated(String associationId, boolean refresh) {
        return allAssociated(associationId, null, refresh);
    }

    /**
     * Returns an AssociatedRecords instance.
     *
     * The <tt>associationId</tt> is the name of the <tt>has-many</tt> or
     * <tt>has-many-through</tt> relation defined in the class.
     *
     * <p>See top of this class for <tt>options</tt> examples.</p>
     *
     * @param associationId association id
     * @param options A string of options.
     * @return the AssociatedRecords instance
     */
    public AssociatedRecords allAssociated(String associationId, String options) {
        return allAssociated(associationId, options, false);
    }

    /**
     * Returns an AssociatedRecords instance.
     *
     * The <tt>associationId</tt> is the name of the <tt>has-many</tt> or
     * <tt>has-many-through</tt> relation defined in the class.
     *
     * <p>See top of this class for <tt>options</tt> examples.</p>
     *
     * @param associationId association id
     * @param options A string of options.
     * @param refresh true if reload database data
     * @return the AssociatedRecords instance
     */
    public AssociatedRecords allAssociated(String associationId, String options, boolean refresh) {
        return getRecordRelation(associationId).allAssociatedRecords(options, refresh);
    }

    /**
     * Returns an AssociatedRecordsInCategory instance.
     *
     * @param category name of the category
     * @return the AssociatedRecordsInCategory instance
     */
    public AssociatedRecordsInCategory allAssociatedInCategory(String category) {
        return allAssociatedInCategory(category, null);
    }

    /**
     * Returns an AssociatedRecordsInCategory instance.
     *
     * @param category name of the category
     * @param refresh true if reload database data
     * @return the AssociatedRecordsInCategory instance
     */
    public AssociatedRecordsInCategory allAssociatedInCategory(String category, boolean refresh) {
        return allAssociatedInCategory(category, null, refresh);
    }

    /**
     * Returns an AssociatedRecordsInCategory instance.
     *
     * @param category name of the category
     * @param type type name in the category
     * @return the AssociatedRecordsInCategory instance
     */
    public AssociatedRecordsInCategory allAssociatedInCategory(String category, String type) {
        return allAssociatedInCategory(category, type, false);
    }

    /**
     * Returns an AssociatedRecordsInCategory instance.
     *
     * @param category name of the category
     * @param type type name in the category
     * @param refresh true if reload database data
     * @return the AssociatedRecordsInCategory instance
     */
    public AssociatedRecordsInCategory allAssociatedInCategory(String category, String type, boolean refresh) {
        RelationManager.getInstance().registerRelations(getClass());
        return new AssociatedRecordsInCategory(this, category, type, refresh);
    }

    /**
     * Returns associated record of this join model based on the type value of
     * the type field. For example, tagging.associatedInCategory("taggable").
     *
     * @param category name of the category
     * @return an associated record for the category
     */
    public AssociatedRecord associatedInCategory(String category) {
        return associatedInCategory(category, false);
    }

    /**
     * Returns associated record of this join model based on the type value of
     * the type field. For example, tagging.associatedInCategory("taggable", true).
     *
     * @param category name of the category
     * @param refresh true if reload database data
     * @return an associated record for the category
     */
    public AssociatedRecord associatedInCategory(String category, boolean refresh) {
        Category categoryInstance = RelationManager.getInstance().getCategory(category);
        if (categoryInstance == null) {
            throw new UnregisteredCategoryException(category);
        }
        String typeField = categoryInstance.getTypeField();
        String typeValue = (String)getField(typeField);
        String model = categoryInstance.getEntityByType(typeValue);
        return associated(model, refresh);
    }

    /**
     * Check if this record is a dependent of a specific record type
     * through primary key. The following must be satisfied:
     *
     * <ol>
     * <li>There must be a belongsTo relation between this record and the parent model type.</li>
     * <li>The child record has a primary key and part or whole of that key is the primary key of the parent model type.</li>
     * </ol>
     *
     * For example, EMPLOYEE (pk=id) and DEPENDENT (pk=id, emp_id);
     *
     * @param parentClz A potential parent model type
     * @return true if it is a true parent.
     */
    public boolean isPKDependentOf(Class parentClz) {
        //condition #1
        Relation r = RelationManager.getInstance().getBelongsToRelationBetween(this.getClass(), parentClz);
        if (r == null) {
            return false;
        }

        //condition #2
        String[] cpk = getPrimaryKeyNames();
        if (cpk == null) return false;

        Object[] left = r.getLeftSideMappingItems();
        for (int i=0; i<left.length; i++) {
            String leftKey = (String)left[i];
            if (!StringUtil.isStringInArray(leftKey, cpk, true)) return false;
        }

        return true;
    }

    /**
     * Check if this record is a dependent of a specific record. To be a
     * dependent of a specific record type, the following must be satisfied:
     *
     * <ol>
     * <li>There must be a belongsTo relation between this record and the parent model type.</li>
     * <li>The cascade property of the parent model must be cascade delete or</li>
     * <li>The child record has a primary key and part or whole of the key is the primary key of the parent model.</li>
     * <li>The record's foreign key must hold parent record's primary key value.</li>
     * </ol>
     * For example, EMPLOYEE (pk=id) and DEPENDENT (pk=id, emp_id);
     *
     * @param parent A potential parent model
     * @return true if it is a true parent.
     */
    public boolean isDependentOf(ActiveRecord parent) {
        if (parent == null || parent.isNewRecord()) return false;

        boolean status = true;

        //condition #1
        Relation r = RelationManager.getInstance().getBelongsToRelationBetween(this.getClass(), parent.getClass());
        if (r == null) {
            return false;
        }

        //condition #2
        Relation reverseRelation = RelationManager.getInstance().getHasManyRelationBetween(parent.getClass(), this.getClass());
        if (reverseRelation != null) {
            if (!reverseRelation.allowCascadeDelete()) {
                return false;
            }
        }
        else {
        	reverseRelation = RelationManager.getInstance().getHasOneRelationBetween(parent.getClass(), this.getClass());
        	if (reverseRelation != null) {
                if (!reverseRelation.allowCascadeDelete()) {
                    return false;
                }
            }
        }

        //condition #3
        String[] cpk = getPrimaryKeyNames();
        if (cpk == null) return false;

        Object[] left = r.getLeftSideMappingItems();
        for (int i=0; i<left.length; i++) {
            String leftKey = (String)left[i];
            if (!StringUtil.isStringInArray(leftKey, cpk, true)) return false;
        }

        //condition #4
        if (status) {
            Object[] fkColumns = r.getLeftSideMappingItems();
            Object[] parentPKColumns = r.getRightSideMappingItems();
            if (fkColumns.length == parentPKColumns.length) {
                int size = fkColumns.length;
                for (int i=0; i<size; i++) {
                    Object fkData = getField((String)fkColumns[i]);
                    Object pkData = parent.getField((String)parentPKColumns[i]);
                    if (!(fkData != null && pkData != null && fkData.toString().equalsIgnoreCase(pkData.toString()))) {
                        status = false;
                        break;
                    }
                }
            }
        }

        return status;
    }

    /**
     * Checks if an instance belongs to another record.
     *
     * To belong to a record, the following conditions must be satisified:
     * <ol>
     * <li>There must be a belongsTo relation between this record and the potential parent record.</li>
     * <li>The record's foreign key must hold parent record's primary key value.</li>
     * </ol>
     *
     * @param parent
     * @return true if the instance is a child of another record
     */
    public boolean isChildOf(ActiveRecord parent) {
        if (parent == null || parent.isNewRecord()) return false;

        //condition #1
        Relation r = RelationManager.getInstance().getBelongsToRelationBetween(this.getClass(), parent.getClass());
        if (r == null) {
            return false;
        }

        //condition #2
        boolean status = true;
        if (status) {
            Object[] fkColumns = r.getLeftSideMappingItems();
            Object[] parentPKColumns = r.getRightSideMappingItems();
            if (fkColumns.length == parentPKColumns.length) {
                int size = fkColumns.length;
                for (int i=0; i<size; i++) {
                    Object fkData = getField((String)fkColumns[i]);
                    Object pkData = parent.getField((String)parentPKColumns[i]);
                    if (!(fkData != null && pkData != null && fkData.toString().equalsIgnoreCase(pkData.toString()))) {
                        status = false;
                        break;
                    }
                }
            }
        }
        return status;
    }

    /**
     * Subclass need to override this method by calling proper relationship
     * setup methods: hasOne, belongsTo, hasMany, hasManyThrough, etc.
     */
    public void registerRelations() {
        ;
    }

    /**
     * Sets belongs-to relation.
     *
     * The association id is the model name of the target class.
     *
     * @param target the class that is associated with.
     */
    public void belongsTo(Class target) {
        String model = ActiveRecordUtil.getModelName(target);
        belongsTo(target, ActiveRecordConstants.key_model + ":" + model);
    }

    /**
     * Sets belongs-to relation with specified properties.
     *
     * The association id of the relation is the model name of the target class.
     *
     * @param target the class that is associated with.
     * @param properties string of association properties.
     */
    public void belongsTo(Class target, String properties) {
        String model = ActiveRecordUtil.getModelName(target);
        RelationManager.getInstance().setupRelation(getClass(), Relation.BELONGS_TO_TYPE, model, target, properties);
    }

    /**
     * Sets belongs-to relation.
     *
     * @param target model name of the associated class.
     */
    public void belongsTo(String target) {
        belongsTo(target, ActiveRecordConstants.key_model + ":" + target);
    }

    /**
     * Sets belongs-to relation with specified properties.
     *
     * <p>
     * <tt>target</tt> parameter can be either the
     * model name of the target or a descriptive string of the target. In
     * the latter case, the <tt>properties</tt> parameter must contain key
     * <tt>model</tt> to indicate the model name of the target unless it can be
     * derieved from the target name.
     *
     * <pre>
     * Example:
     *      target    properties
     *      ------    ----------
     *      friend    model:person
     * </pre>
     * </p>
     *
     * <p>
     * Example propertie string:
     * In a property string, each name-value pair is separated by ';'
     * character, while within each name-value pair, name and value strings
     * are separated by ':' character.
     *
     * For example, a property string like the following
     * <blockquote><pre>
     *      mapping: order_id=id;
     *      conditions_sql: id in (1, 2, 3); include: category, user;
     *      order_by: first_name, salary desc; cascade: delete
     * </pre></blockquote>
     *
     * will be converted to a HashMap with the following entries:
     * <blockquote><pre>
     *      key                 value
     *      -------------       ---------------
     *      mapping         =>  order_id=id
     *      conditions_sql  =>  id in (1, 2, 3)
     *      include         =>  category, user
     *      order_by        =>  first_name, salary desc
     *      cascade         =>  delete
     * </pre></blockquote>
     * For a complete list of properties, see top of the class or developer guide.
     * </p>
     *
     * @param target      target name of the associated class.
     * @param properties  association properties.
     */
    public void belongsTo(String target, String properties) {
        RelationManager.getInstance().setupRelation(getClass(), Relation.BELONGS_TO_TYPE, target, null, properties);
    }

    /**
     * Sets up a category with default id field and type field.
     *
     * This method assumes that the id field is ${category}_id and the type
     * field is ${category}_type.
     *
     * @param category name of the category
     */
    public void belongsToCategory(String category) {
        String idField = category + "_id";
        String typeField = category + "_type";
        belongsToCategory(category, idField, typeField);
    }

    /**
     * Sets up a category.
     *
     * @param idField name of the id column of the category center table
     * @param typeField name of the type column of the category center table
     * @param category name of the category
     */
    public void belongsToCategory(String category, String idField, String typeField) {
        RelationManager.getInstance().registerCategory(getClass(), category, idField, typeField);
    }

    /**
     * Sets has-one relation
     *
     * @param target the class that is associated with.
     */
    public void hasOne(Class target) {
        String model = ActiveRecordUtil.getModelName(target);
        hasOne(target, ActiveRecordConstants.key_model + ":" + model);
    }

    /**
     * Sets has-one relation with specified properties.
     *
     * The association id of the relation is the model name of the target class.
     *
     * @param target the class that is associated with.
     * @param properties string of association properties.
     */
    public void hasOne(Class target, String properties) {
        String model = ActiveRecordUtil.getModelName(target);
        RelationManager.getInstance().setupRelation(getClass(), Relation.HAS_ONE_TYPE, model, target, properties);
    }

    /**
     * Sets has-one relation.
     *
     * @param targetModelName model name of the associated class.
     */
    public void hasOne(String targetModelName) {
        hasOne(targetModelName, ActiveRecordConstants.key_model + ":" + targetModelName);
    }

    /**
     * Sets has-one relation with specified properties.
     *
     * <p>
     * <tt>target</tt> parameter can be either the
     * model name of the target or a descriptive string of the target. In
     * the latter case, the <tt>properties</tt> parameter must contain
     * key <tt>model</tt> to indicate the model name of the target.
     *
     * <pre>
     * Example:
     *      target    properties
     *      ------    ----------
     *      setting   model:profile
     * </pre>
     * </p>
     *
     * <p>
     * Example propertie string:
     * In a property string, each name-value pair is separated by ';'
     * character, while within each name-value pair, name and value strings
     * are separated by ':' character.
     *
     * For example, a property string like the following
     * <blockquote><pre>
     *      mapping: id=order_id;
     *      conditions_sql: id in (1, 2, 3); include: category, user;
     *      order_by: first_name, salary desc; cascade: delete
     * </pre></blockquote>
     *
     * will be converted to a HashMap with the following entries:
     * <blockquote><pre>
     *      key                 value
     *      -------------       ---------------
     *      mapping         =>  id=order_id
     *      conditions_sql  =>  id in (1, 2, 3)
     *      include         =>  category, user
     *      order_by        =>  first_name, salary desc
     *      cascade         =>  delete
     * </pre></blockquote>
     * For a complete list of properties, see top of the class or developer guide.
     * </p>
     *
     * @param target      target name of the associated class.
     * @param properties  association properties.
     */
    public void hasOne(String target, String properties) {
        RelationManager.getInstance().setupRelation(getClass(), Relation.HAS_ONE_TYPE, target, null, properties);
    }

    /**
     * Sets has-many relation
     *
     * @param target the class that is associated with.
     */
    public void hasMany(Class target) {
        String model = ActiveRecordUtil.getModelName(target);
        hasMany(target, ActiveRecordConstants.key_model + ":" + model);
    }

    /**
     * Sets has-many relation with specified properties.
     *
     * The association id of the relation is the plural form of the model name of the target class.
     *
     * @param target the class that is associated with.
     * @param properties string of association properties.
     */
    public void hasMany(Class target, String properties) {
        String model = ActiveRecordUtil.getModelName(target);
        RelationManager.getInstance().setupRelation(getClass(), Relation.HAS_MANY_TYPE, WordUtil.pluralize(model), target, properties);
    }

    /**
     * Sets has-many relation.
     *
     * @param targets plural form of a model name of the associated class.
     */
    public void hasMany(String targets) {
        String targetModel = WordUtil.singularize(targets);
        hasMany(targets, ActiveRecordConstants.key_model + ":" + targetModel);
    }

    /**
     * Sets has-many relation with specified properties.
     *
     * <p>
     * <tt>targets</tt> parameter can be either a
     * plural form of the model name of the target or a descriptive string
     * of the target. In the latter case, the <tt>properties</tt> parameter
     * must contain key <tt>model</tt> to indicate the model name of the target.
     *
     * <pre>
     * Example:
     *      targets    properties
     *      -------    ----------
     *      people    model:person
     * </pre>
     * </p>
     *
     * <p>
     * Example propertie string:
     * In a property string, each name-value pair is separated by ';'
     * character, while within each name-value pair, name and value strings
     * are separated by ':' character.
     *
     * For example, a property string like the following
     * <blockquote><pre>
     *      mapping: id=order_id;
     *      conditions_sql: id in (1, 2, 3); include: category, user;
     *      order_by: first_name, salary desc; cascade: delete
     * </pre></blockquote>
     *
     * will be converted to a HashMap with the following entries:
     * <blockquote><pre>
     *      key                 value
     *      -------------       ---------------
     *      mapping         =>  id=order_id
     *      conditions_sql  =>  id in (1, 2, 3)
     *      include         =>  category, user
     *      order_by        =>  first_name, salary desc
     *      cascade         =>  delete
     * </pre></blockquote>
     * For a complete list of properties, see top of the class or developer guide.
     * </p>
     *
     * @param targets        plural form of target name of the associated class.
     * @param properties     association properties.
     */
    public void hasMany(String targets, String properties) {
        RelationManager.getInstance().setupRelation(getClass(), Relation.HAS_MANY_TYPE, targets, null, properties);
    }

    /**
     * Sets has-many-through relation.
     *
     * <p>
     * This is equvilent to {@link #hasManyThrough(java.lang.String, java.lang.String)}
     * method with the plural form of the model name as the association name.
     * </p>
     *
     * @param target  target class.
     * @param through middleC classs.
     */
    public void hasManyThrough(Class target, Class through) {
        hasManyThrough(target, through, null);
    }

    /**
     * Sets has-many-through relation.
     *
     * <p>
     * This is equvilent to {@link #hasManyThrough(java.lang.String, java.lang.String, java.lang.String)}
     * method with the plural form of the model name as the association name.
     * </p>
     *
     * @param target      target class.
     * @param through     middleC classs.
     * @param properties  properties string.
     */
    public void hasManyThrough(Class target, Class through, String properties) {
        hasManyThrough(target, through, properties, null);
    }

    /**
     * Sets has-many-through relation.
     *
     * <p>
     * This is equvilent to {@link #hasManyThrough(java.lang.String, java.lang.String, java.lang.String, java.util.Map)}
     * method with the plural form of the model name as the association name.
     * </p>
     *
     * @param target      target class.
     * @param through     middleC classs.
     * @param properties  properties string.
     * @param joinInputs data map for the join table.
     */
    public void hasManyThrough(Class target, Class through, String properties, Map joinInputs) {
        String targetModel = ActiveRecordUtil.getModelName(target);
        String targets = WordUtil.pluralize(targetModel);
        String throughModel = ActiveRecordUtil.getModelName(through);
        String throughs = WordUtil.pluralize(throughModel);
        RelationManager.getInstance().setupHasManyThroughRelation(getClass(), targets, throughs, properties, joinInputs);
    }

    /**
     * Sets has-many-through relation.
     *
     * <p>
     * There are two pre-requisits for setting up a has-many-through relation:
     * <ul>
     *   <li>The owner class must have a has-many relation named
     *   <tt>throughAssociationId</tt> with the middleC class.</li>
     *   <li>The middleC class must have a either a belongs-to or a has-many
     *   relation named <tt>targets</tt> with the target class.</li>
     * </ul>
     * </p>
     *
     * @param targets              plural form of target name of the associated class.
     * @param throughAssociationId the name of the association that is in the middle.
     */
    public void hasManyThrough(String targets, String throughAssociationId) {
        hasManyThrough(targets, throughAssociationId, null);
    }

    /**
     * Sets has-many-through relation with specified properties.
     *
     * <p>
     * There are two pre-requisits for setting up a has-many-through relation:
     * <ul>
     *   <li>The owner class must have a has-many relation named
     *   <tt>throughAssociationId</tt> with the middleC class.</li>
     *   <li>The middleC class must have a either a belongs-to or a has-many
     *   relation named <tt>targets</tt> with the target class.</li>
     * </ul>
     * </p>
     *
     * Example propertie string:
     * In a property string, each name-value pair is separated by ';'
     * character, while within each name-value pair, name and value strings
     * are separated by ':' character.
     *
     * For example, a property string like the following
     * <blockquote><pre>
     *      conditions_sql: id in (1, 2, 3); include: category, user;
     *      order_by: first_name, salary desc; cascade: delete
     * </pre></blockquote>
     *
     * will be converted to a HashMap with the following entries:
     * <blockquote><pre>
     *      key                 value
     *      -------------       ---------------
     *      conditions_sql  =>  id in (1, 2, 3)
     *      include         =>  category, user
     *      order_by        =>  first_name, salary desc
     *      cascade         =>  delete
     * </pre></blockquote>
     * For a complete list of properties, see top of the class or developer guide.
     *
     * @param targets              plural form of target name of the associated class.
     * @param throughAssociationId the name of the association that is in the middle.
     * @param properties           properties string.
     */
    public void hasManyThrough(String targets, String throughAssociationId, String properties) {
        hasManyThrough(targets, throughAssociationId, properties, null);
    }

    /**
     * Sets has-many-through relation with specified properties and join
     * through table data.
     *
     * <p>
     * There are two pre-requisits for setting up a has-many-through relation:
     * <ul>
     *   <li>The owner class must have a has-many relation named
     *   <tt>throughAssociationId</tt> with the middleC class.</li>
     *   <li>The middleC class must have a either a belongs-to or a has-many
     *   relation named <tt>targets</tt> with the target class.</li>
     * </ul>
     * </p>
     *
     * See description of {@link #hasManyThrough(java.lang.String, java.lang.String, java.lang.String)}
     * method for details about <tt>properties</tt>.
     *
     * @param targets              plural form of target name of the associated class.
     * @param throughAssociationId the name of the association that is in the middle.
     * @param properties           properties string.
     * @param joinInputs          data map for the join table.
     */
    public void hasManyThrough(String targets, String throughAssociationId, String properties, Map joinInputs) {
        RelationManager.getInstance().setupHasManyThroughRelation(getClass(), targets, throughAssociationId, properties, joinInputs);
    }

    /**
     * This method adds a bunch of methods in many classes.
     * <ol>
     * <li> A has-many-through association from owner to each target class.</li>
     * <li> A has-many association from each target to through class.</li>
     * <li> A has-many-through association from each target to owner class.</li>
     * <li> A belongs-to association from through to each target class.</li>
     * </ol>
     *
     * In order to establish the associations, the method assumes the following:
     * <ol>
     * <li> The type value of the category type column is the model name of
     *      each corresponding target class.</li>
     * <li> The primary key of each target class is "id".</li>
     * <li> The mapping string between each target class and through class is
     *      "id= category's id column".</li>
     * <li> The association property from each target to through contains "cascade: delete".</li>
     * </ol>
     *
     * <p>
     * If any of the above assumptions are not satisfied, you need to use the
     * other <tt>hasManyInCategoryThrough </tt> method which gives you more
     * control on specifying the associations.
     * </p>
     *
     * <p>Example usage: </p>
     * <p>Assuming there are image files and text files in a folder. We create
     * three models: images, texts, folders. We also use linkings model to
     * link folders with images and texts files. We will create the following
     * classes:</p>
     *
     * <pre>
     *  CREATE TABLE linkings (
     *      id INTEGER AUTO_INCREMENT,
     *      folder_id INTEGER,
     *      linkable_id INTEGER,
     *      linkable_type VARCHAR(20),
     *      PRIMARY KEY(id)
     *  )
     *
     *  class Linking extends ActiveRecord {
     *      public void registerRelations() {
     *          belongsTo(Folder.class);
     *          belongsToCategory("linkable");
     *      }
     *  }
     *
     *  class Folder extends ActiveRecord {
     *      public void registerRelations() {
     *          hasMany(Linking.class);
     *          hasManyInCategoryThrough(Folder.class,
     *                                   new Class[]{Image.class, Text.class},
     *                                   "linkable", Linking.class);
     *      }
     *  }
     *
     *  class Image extends ActiveRecord {
     *  }
     *
     *  class Text extends ActiveRecord {
     *  }
     * </pre>
     *
     * The following codes show how to get total of ownership for a customer:
     * <pre>
     *      //Find all ownerships of a customer
     *      ActiveRecord customerHome = ActiveRecordUtil.getHomeInstance(Customer.class);
     *      ActiveRecord customer = customerHome.find("id=1");
     *      int total = customer.allAssociatedInCategory("ownerable").size();
     * </pre>
     *
     * It is also easy to add a dvd to the ownership of the customer:
     * <pre>
     *      //Assign a dvd to a customer
     *      ActiveRecord dvdHome = ActiveRecordUtil.getHomeInstance(Dvd.class);
     *      ActiveRecord dvd = dvdHome.find("id=4");
     *      List dvds = customer.allAssociatedInCategory("ownerable").add(dvd).getRecords();
     * </pre>
     *
     * @param targets       array of target classes
     * @param category      the category which the targets act as
     * @param through       the middle join class between owner and targets
     */
    public void hasManyInCategoryThrough(Class[] targets,
                                         String category, Class through) {
        if (targets == null || targets.length == 0) {
            throw new IllegalArgumentException("Target array cannot be empty.");
        }

        //make sure category center is loaded first
        RelationManager.getInstance().registerRelations(through);

        Category categoryInstance = RelationManager.getInstance().getCategory(category);
        if (categoryInstance == null) {
            throw new UnregisteredCategoryException(category);
        }
        String idField = categoryInstance.getIdField();
        String typeField = categoryInstance.getTypeField();
        String cTableName = ActiveRecordUtil.getTableName(through);

        int targetTotal = targets.length;
        String[] abProperties = new String[targetTotal];
        String[] types = new String[targetTotal];
        String relationType = Relation.HAS_MANY_TYPE;
        String[] bcProperties = new String[targetTotal];
        Map[] joinInputs = new HashMap[targetTotal];
        String[] cbProperties = new String[targetTotal];
        String cbMapping = ActiveRecordConstants.key_mapping + ": " + idField + "=id; ";

        for (int i=0; i<targetTotal; i++) {
            types[i] = ActiveRecordUtil.getModelName(targets[i]);
            String throughTypeCondition = ActiveRecordConstants.key_conditions_sql + ": " + cTableName + "." + typeField + "='" + types[i] + "'";
            abProperties[i] = throughTypeCondition;
            bcProperties[i] = ActiveRecordConstants.key_mapping + ": id=" + idField + "; " +
                              throughTypeCondition + "; " + ActiveRecordConstants.key_cascade + ": delete";
            Map inputs = new HashMap();
            inputs.put(typeField, types[i]);
            joinInputs[i] = inputs;
            cbProperties[i] = cbMapping;
        }

        //baProperties are null.
        hasManyInCategoryThrough(targets, category, through, joinInputs,
                                 abProperties, types, relationType,
                                 bcProperties, joinInputs, null, null);
    }

    /**
     * This method adds a bunch of methods in many classes.
     * <pre>
     * <li> A has-many-through association from owner to each target class.</li>
     * <li> A has-many association from each target to through class.</li>
     * <li> A has-many-through association from each target to owner class.</li>
     * <li> A belongs-to association from through to each target class.</li>
     * </pre>
     *
     * Assuming owner class is A, target class is B, through class is C,
     * <pre>
     * <tt>abProperties</tt> is join properties from A to B,
     * <tt>bcProperties</tt> is join properties from B to C,
     * <tt>cbProperties</tt> is join properties from C to B,
     * <tt>baProperties</tt> is join properties from B to A.
     * </pre>
     *
     * @param targets       array of target classes
     * @param category      the category which the targets act as
     * @param through       the middle join class between owner and targets
     * @param acJoinInputs array of data map for the join through table.
     * @param abProperties  properties from owner to target class
     * @param types         array of join types in the category, default to model name
     * @param relationType  either has-many or has-one
     * @param bcProperties  array of properties from each target to through class
     * @param bcJoinInputs array of data map for the join through table.
     * @param cbProperties  array of properties from through to each target class
     * @param baProperties  array of properties from each target to owner class
     */
    public void hasManyInCategoryThrough(Class[] targets,
                 String category, Class through, Map[] acJoinInputs,
                 String[] abProperties, String[] types, String relationType,
                 String[] bcProperties, Map[] bcJoinInputs, String[] cbProperties,
                 String[] baProperties) {
        if (targets == null || targets.length == 0) {
            throw new IllegalArgumentException("Target array cannot be empty.");
        }

        //make sure category center is loaded first
        RelationManager.getInstance().registerRelations(through);

        Category categoryInstance = RelationManager.getInstance().getCategory(category);
        if (categoryInstance == null) {
            throw new UnregisteredCategoryException(category);
        }
        String idField = categoryInstance.getIdField();
        String typeField = categoryInstance.getTypeField();
        String cTableName = ActiveRecordUtil.getTableName(through);

        //prepare
        int targetTotal = targets.length;
        if (abProperties == null) abProperties = new String[targetTotal];
        if (bcProperties == null) bcProperties = new String[targetTotal];
        if (cbProperties == null) cbProperties = new String[targetTotal];
        if (baProperties == null) baProperties = new String[targetTotal];

        String cbMappingProperty = ActiveRecordConstants.key_mapping + ": " + idField + "=id; ";

        //#1, #2, #4, #3
        for (int i=0; i<targetTotal; i++) {
            Class target = targets[i];
            String targetEntityName = ActiveRecordUtil.getModelName(targets[i]);

            String type = "";
            if (types != null) type = types[i];
            if (type == null) type = targetEntityName;
            String throughTypeCondition = ActiveRecordConstants.key_conditions_sql + ": " + cTableName + "." + typeField + "='" + type + "'";

            String abProperty = abProperties[i];
            if (abProperty == null) {
                abProperty = throughTypeCondition;
            }
            else {
                if (abProperty.indexOf(ActiveRecordConstants.key_conditions_sql) == -1) {
                    abProperty = throughTypeCondition + "; " + abProperty;
                }
            }

            String bcProperty = bcProperties[i];
            if (bcProperty == null) {
                bcProperty = ActiveRecordConstants.key_mapping + ": id=" + idField + "; " +
                             throughTypeCondition + "; cascade: delete";
            }
            else {
                if (bcProperty.indexOf(ActiveRecordConstants.key_conditions_sql) == -1) {
                    bcProperty = throughTypeCondition + "; " + bcProperty;
                }

                if (bcProperty.indexOf(ActiveRecordConstants.key_mapping) == -1) {
                    bcProperty = ActiveRecordConstants.key_mapping + ": id=" + idField + "; " + bcProperty;
                }

                if (bcProperty.indexOf(ActiveRecordConstants.key_cascade) == -1) {
                    bcProperty = ActiveRecordConstants.key_cascade + ": delete" + "; " + bcProperty;
                }
            }

            Map acJoinInputsMap = acJoinInputs[i];
            if (acJoinInputsMap == null) {
                acJoinInputsMap = new HashMap();
            }
            if (acJoinInputsMap.size() == 0) {
                acJoinInputsMap.put(typeField, type);
            }

            String cbProperty = cbProperties[i];
            if (cbProperty == null) {
                cbProperty = cbMappingProperty;
            }
            else {
                if (cbProperty.indexOf(ActiveRecordConstants.key_mapping) == -1) {
                    cbProperty = cbMappingProperty + "; " + cbProperty;
                }
            }

            //#2. A has-many association from each target to through class.
            //#4. A belongs-to association from through to each target class.
            ActiveRecord targetHome = ActiveRecordUtil.getHomeInstance(target);
            targetHome.actAsInCategory(type, category,
                    relationType, through, bcProperty, cbProperty);

            //#1. A has-many-through association from owner to each target class.
            //Note: need to add a has-many relation between owner and through
            //      as this is a prerequisit for setting up a has-many-through relation.
            if (RelationManager.getInstance().existsHasManyRelationBetween(getClass(), through)) {
                hasMany(through);
            }
            hasManyThrough(target, through, abProperty, acJoinInputsMap);

            Map bcJoinInputsMap = bcJoinInputs[i];
            if (bcJoinInputsMap == null) {
                bcJoinInputsMap = new HashMap();
            }
            if (bcJoinInputsMap.size() == 0) {
                bcJoinInputsMap.put(typeField, type);
            }

            //#3. A has-many-through association from each target to owner class.
            //Note: need to add a belongs-to relation between through and owner
            //      as this is a prerequisit for setting up a has-many-through relation.
            if (RelationManager.getInstance().existsBelongsToRelationBetween(through, getClass())) {
                ActiveRecord throughHome = ActiveRecordUtil.getHomeInstance(through);
                throughHome.belongsTo(getClass());
            }
            targetHome.hasManyThrough(getClass(), through, baProperties[i], bcJoinInputsMap);
        }
    }

    /**
     * Returns true if the field is a database table field.
     *
     * @param fieldName the field name to check.
     * @return boolean true if the field is a database table field.
     */
    public boolean isColumnField(String fieldName) {
        boolean column = false;
        if (fieldName != null) {
            column = rowInfo.isValidColumnName(fieldName);
        }
        return column;
    }

    /**
     * Returns true if the field is an extra field.
     *
     * @param fieldName the field name to check.
     * @return boolean true if the field is an extra field.
     */
    public boolean isExtraField(String fieldName) {
        boolean extra = false;
        if (fieldName != null) {
            extra = extraFields.contains(fieldName.toUpperCase());
        }
        return extra;
    }

    /**
     * Returns a list of modified field names.
     *
     * @return list of modified field names
     */
    public List getModifiedFields() {
        return modifiedColumns;
    }

    /**
     * Checks if a field is changed.
     *
     * @param fieldName the field name to check
     * @return true if the field is changed.
     */
    public boolean isFieldChanged(String fieldName) {
        verifyExistenceOfField(fieldName);
        boolean changed = false;
        if (isLegalField(fieldName)) {
            changed = modifiedColumns.contains(fieldName.toUpperCase());
        }
        return changed;
    }

    /**
     * Checks whether a field is a legal field of this model.
     *
     * @param fieldName the field name to check
     * @return true if the field is either a column or an extra field.
     */
    public boolean isLegalField(String fieldName) {
        boolean legal = true;
        if (!isExtraField(fieldName) && !rowInfo.isValidColumnName(fieldName)) {
            legal = false;
        }
        return legal;
    }

    /**
     * Checks if a field name exists in an ActiveRecord class.
     *
     * @param fieldName    a field name
     */
    public void verifyExistenceOfField(String fieldName) {
        if (!isLegalField(fieldName)) {
            throw new GenericException("Field [" + fieldName + "] is not an attribute of class " + getClass().getName() + ".");
        }
    }

    /**
     * Returns default database connection name. This name is defined in the
     * database properties file.
     *
     * @return default database connection name
     */
    public static String getDefaultConnectionName() {
        return DatabaseConfig.getInstance().getDefaultDatabaseConnectionName();
    }

    /**
     * <p>
     * Returns a full table name in the database. By default the table name is
     * a short class name. Subclass may override this method to provide a more
     * meaningful table name. </p>
     *
     * <p>
     * The default table name is a short version of current class name. Java
     * class name starts with capital letter. If the class name has more than
     * one capital letter, an underscore is added as part of the table name.</p>
     *
     * <pre>
     * Examples:
     *
     *      Class Name:                             Table Name:
     *      ---------------------------------------------------
     *      com.example.model.User                  users
     *      com.example.model.LineItem              line_items
     *      com.example.model.UserAccount           user_accounts
     *      com.example.model.UserURL               user_urls
     * </pre>
     *
     * @return String table name
     */
    public String getTableName() {
        if (tableName == null || "".equals(tableName)) {
            tableName = getDefaultTableName();
        }

        tableName = DatabaseConfig.getInstance().getFullTableName(tableName);

        return tableName;
    }

    /**
     * Returns a slim version of table name.
     *
     * <p>
     * For example, if the table name is "CRM_users_US" which has a prefix
     * "CRM_" and a suffix "_US", the returned slim table name is just
     * "users". Both the prefix and the suffix are removed in the return value.
     * </p>
     *
     * @return a slim version of table name.
     */
    public String getSlimTableName() {
        return DatabaseConfig.getInstance().getSlimTableName(tableName);
    }


    /**
     * Sets populating rules for primary keys.
     *
     * Subclass may override this method to use one of the four following ways
     * to provide primary key values.
     *
     * There are four ways to set up the name-rule pair for primary key:
     * 1. Do nothing. This is the default. that means the database will
     *    autogenerate a primary key. This feature is available on MySQL, but
     *    not on Oracle yet. If you use this feature, that means the primary
     *    key can not be a composite field.
     *
     * 2. If the primary key value is a result of sql query,
     *    the entry may look like this:
     *            ("id", "sql=SELECT max(id)+1 FROM employee");
     *
     * 3. If the primary key value is a result of a named sql query,
     *    the entry may look like this:
     *            ("id", "sqlkey=employee_id_query");
     *    where the value "employee_id_query" is a named query in
     *    sql.properties file.
     *
     * 4. Use a fixed value:
     *            Map("id", "1000");
     *
     * @return a data map for primary keys
     */
    protected Map getPrimaryKeyRules() {
        return null;
    }

    protected String getDeleteSQL() {
        return getTableInfo().getHeader().getDeleteSqlInJDBCStyle();
    }

    /**
     * prepares initial values for the record's non-null non-primary key
     * writable or non-readonly fields if they haven't been initialized.
     *
     * For nullable fields(columns), it is up to database to set
     * the default value.
     *
     * Subclass may either override this method or
     * getDefaultDataByClassType(String) method to provide its own specific
     * initial values for non-null fields, or use the setData methods
     * before calling create();
     */
    protected Map getInitializedValues() {
        Map initMap = new HashMap();
        int dimension = rowInfo.getDimension();
        for(int colIndex=0; colIndex<dimension; colIndex++) {
            ColumnInfo ci = rowInfo.getColumnInfo(colIndex);
            if (!ci.isPrimaryKey() &&
                ci.isNotNull() &&
               (ci.isWritable() || !ci.isReadOnly())) {
                String colName = ci.getColumnName();
                if(getField(colIndex) == null) {
                    Object initialData = getDefaultDataByClassType(ci.getColumnClassName());
                    initMap.put(colName, initialData);

                    //log.debug("set initial value for column " + colName + " as " + initialData);
                }
            }
        }

        return initMap;
    }

    /**
     * prepares default value for class type
     *
     * The following class types are handled:
     *      BigDecimal, BigInteger, Byte, Double, Float, Integer, Long, Short
     *      String, Character, Date, Timestamp, Time
     *
     * The default values for each class type are:
     *
     *      java.math.BigInteger: 0
     *      java.math.BigDecimal: 0
     *      java.lang.Integer: 0
     *      java.lang.Long: 0
     *      java.lang.Short: 0
     *      java.lang.Double: 0.0
     *      java.lang.Float: 0.0
     *      java.lang.Byte: 0
     *      java.lang.Character: one char blank space
     *      java.util.Date: current system date (e.g. Thu May 25 15:30:17 EDT 2006)
     *      java.sql.Date: current system date (e.g. 2006-05-25)
     *      java.sql.Timestamp: current system timestamp (e.g. 2006-05-25 15:30:17.707)
     *      java.sql.Time: 00:00:00
     *
     * Subclass may override or extend this method to provide desired default
     * values.
     *
     * @param classTypeName
     * @return Object An object contains default value of a specific class type.
     */
    protected Object getDefaultDataByClassType(String classTypeName) {
        if (classTypeName == null)
            throw new IllegalArgumentException("Invalid class type name: " +
                                                classTypeName);

        Object value = null;
        if("java.math.BigInteger".equals(classTypeName)) {
            value = new java.math.BigInteger("0");
        }
        else if("java.math.BigDecimal".equals(classTypeName)) {
            value = new java.math.BigDecimal(0);
        }
        else if("java.lang.Integer".equals(classTypeName)) {
            value = new Integer(0);
        }
        else if("java.lang.Long".equals(classTypeName)) {
            value = new Long(0);
        }
        else if("java.lang.Short".equals(classTypeName)) {
            value = new Short("0");
        }
        else if("java.lang.Double".equals(classTypeName)) {
            value = new Double(0.0);
        }
        else if("java.lang.Float".equals(classTypeName)) {
            value = new Float(0.0);
        }
        else if("java.lang.Byte".equals(classTypeName)) {
            value = new Byte("0");
        }
        else if("java.lang.Character".equals(classTypeName)) {
            value = new Character(' ');
        }
        else if("java.util.Date".equals(classTypeName)) {
            value = new java.util.Date();
        }
        else if("java.sql.Date".equals(classTypeName)) {
            value = new java.sql.Date((new java.util.Date()).getTime());
        }
        else if("java.sql.Timestamp".equals(classTypeName)) {
            value = new java.sql.Timestamp((new java.util.Date()).getTime());
        }
        else if("java.sql.Time".equals(classTypeName)) {
            value = java.sql.Time.valueOf("00:00:00");
        }
        return value;
    }

    private static SqlService getSqlService() {
        return SqlServiceConfig.getSqlService();
    }

    /**
     * Data for the protected fields are not affected by massive setData method.
     *
     * Subclass need to override this method by calling setProtectedFields
     * method to declare protected fields.
     */
    protected void declaresProtectedFields() {
        ;
    }

    /**
     * Extra fields are not stored in database.
     *
     * Subclass need to override this method by calling setExtraFields
     * method to declare extra fields.
     */
    protected void declaresExtraFields() {
        ;
    }

    /**
     * initializes the record
     */
    private void initialize(String connectionName, String table) {
        rowInfo = lookupAndRegister(connectionName, table).getHeader();
        rowData = new RowData(rowInfo, null);

        //initialize other column values
        initializeFields();

        dirty = false;//reset dirty as it was set in setData()
        existInDatabase = false;
        freezed = false;

        //load protected fields
        declaresProtectedFields();

        //load extra fields
        declaresExtraFields();
    }

    /**
     * initializes the record's non-null non-primary key fields if
     * they haven't been initialized.
     *
     * For nullable fields(columns), it is up to database to set
     * the default value.
     */
    private void initializeFields() {
        Map dataMap = getInitializedValues();

        if(dataMap.size() > 0) {
            setData(dataMap);
        }
    }

    /**
     * <p>Returns default table name. </p>
     *
     * <p>The default table name is a short version of current class name. It is
     * always in pluralized form unless otherwise indicated by the property
     * <tt>use.plural.table.name</tt> in property file. </p>
     *
     * <p>Java class name starts with capital letter. If the class name has more
     * than one capital letter, an underscore is added as part of the table
     * name. </p>
     *
     * <pre>
     * Examples:
     *      Class Name:                             Table Name:
     *      ---------------------------------------------------
     *      com.example.model.User                  users
     *      com.example.model.LineItem              line_items
     *      com.example.model.UserAccount           user_accounts
     *      com.example.model.UserURL               user_urls
     * </pre>
     *
     * @return String
     */
    private String getDefaultTableName() {
        String className = Util.getShortClassName(this.getClass());

        String tname = className;
        if (DatabaseConfig.getInstance().usePluralTableName()) {
            tname = WordUtil.tableize(tname);
        }
        else {
            tname = WordUtil.underscore(tname);
        }

        return tname;
    }

    /**
     * Sets record data when creating an instance of the record
     */
    void populateDataFromDatabase(RowData rd) {
        if (rd == null) {
            rowData.clearData();
        }
        else {
            rowData = rd;

            Map pkDataMap = rowData.getPrimaryKeyDataMap();
            if (pkDataMap == null || pkDataMap.size() == 0) {
                String[] pkNames = getPrimaryKeyNames();
                rowData.getRowInfo().setPrimaryKeyColumns(pkNames);
                pkDataMap = rowData.getPrimaryKeyDataMap();
            }

            latestDbRowData = new RowData(rd.getRowInfo(), null);
            existInDatabase = true;
        }
    }

    /**
     * Returns table meta data. <tt>table</tt> is a full table name.
     */
    private TableInfo lookupAndRegister(String connectionName, String table) {
        TableInfo ti = SqlExpressUtil.lookupAndRegisterTable(connectionName, table);
        if (ti == null) {
        	throw new IllegalArgumentException("Failed to look up table \"" +
        			table + "\" for connection named " + connectionName);
        }
        rowInfo = ti.getHeader();
        DBStore.getInstance().addClassTableMapping(ActiveRecordUtil.getFullClassName(this.getClass()), table);
        return ti;
    }

    /**
     * prepareInsertSQL
     */
    private int prepareInsertSQL(RowData rd, Map outs, StringBuffer strBuffer, boolean autoPopulatePrimaryKey) {
        RowInfo ri = rd.getRowInfo();
        if (ri == null)
            throw new IllegalArgumentException("Error in prepareInsertSQL: no RowInfo.");

        StringBuffer names = new StringBuffer();
        StringBuffer values = new StringBuffer();
        int maxSize = rd.getSize();
        int positionIndex = 0;
        ColumnInfo ci = null;
        int i = 0;
        for(i=0; i<maxSize-1; i++) {
            ci = ri.getColumnInfo(i);
            if (ci.isPrimaryKey() && autoPopulatePrimaryKey) continue;
            if (ci.isReadOnly() || !ci.isWritable()) continue;

            names.append(ci.getColumnName()).append(", ");
            values.append("?, ");
            positionIndex = positionIndex + 1;
            outs.put(positionIndex+"", rd.getField(i));
        }

        //the last column: i=maxSize-1
        ci = ri.getColumnInfo(i);
        if (!ci.isReadOnly() && ci.isWritable()) {
            names.append(ci.getColumnName()).append("");
            values.append("?");
            positionIndex = positionIndex + 1;
            outs.put(positionIndex+"", rd.getField(i));
        }

        String namesList = names.toString();
        if (namesList.endsWith(", ")) {
            namesList = namesList.substring(0,namesList.lastIndexOf(','));
        }
        String valuesList = values.toString();
        if (valuesList.endsWith(", ")) {
            valuesList = valuesList.substring(0,valuesList.lastIndexOf(','));
        }

        strBuffer.append("(").append(namesList).append(") VALUES (").append(valuesList).append(")");
        return positionIndex;
    }

    /**
     * prepareWhereClause
     */
    private int prepareWhereClause(int startPosition, Map ins, Map outs, StringBuffer strBuffer) {
        int maxSize = ins.size();
        int count = 0;
        for(Iterator it = ins.keySet().iterator(); it.hasNext();) {
            String keyName = (String) it.next();
            Object valueData = ins.get(keyName);

            count = count + 1;
            if (maxSize != count)
                strBuffer.append(keyName).append(" = ? AND ");
            else
                strBuffer.append(keyName).append(" = ? ");

            outs.put(startPosition+"", valueData);
            startPosition = startPosition + 1;
        }

        return startPosition;
    }

    /**
     * prepareSetSQL
     */
    private int prepareSetSQL(int startPosition, RowData rd, Map outs, StringBuffer strBuffer) {
        RowInfo ri = rd.getRowInfo();
        if (ri == null)
            throw new IllegalArgumentException("Error in prepareSetSQL: no RowInfo.");

        int maxSize = rd.getSize();

        int i = 0;
        ColumnInfo ci = null;
        for(i=0; i<maxSize-1; i++) {
            ci = ri.getColumnInfo(i);
            if (ci.isReadOnly() || !ci.isWritable() || ci.isPrimaryKey()) continue;

            strBuffer.append(ci.getColumnName()).append(" = ?, ");
            outs.put(startPosition+"", rd.getField(i));
            startPosition = startPosition + 1;
        }

        //the last column: i=maxSize-1
        ci = ri.getColumnInfo(i);
        if (!ci.isReadOnly() && ci.isWritable() && !ci.isPrimaryKey()) {
            strBuffer.append(ci.getColumnName()).append(" = ? ");
            outs.put(startPosition+"", rd.getField(i));
            startPosition = startPosition + 1;
        }

        return startPosition;
    }

    /**
     * prepareSetSQL
     */
    private int prepareSetSQL(int startPosition, Map fieldData, Map outs, StringBuffer strBuffer) {
        RowInfo ri = rowInfo;
        if (ri == null)
            throw new IllegalArgumentException("Error in prepareSetSQL: no RowInfo.");

        if (fieldData == null || fieldData.size() == 0) return startPosition;

        ColumnInfo ci = null;
        for(Iterator it = fieldData.keySet().iterator(); it.hasNext();) {
            String field = (String)it.next();
            ci = ri.getColumnInfo(field);
            if (ci.isReadOnly() || !ci.isWritable() || ci.isPrimaryKey()) continue;

            strBuffer.append(ci.getColumnName()).append(" = ?, ");
            outs.put(startPosition+"", fieldData.get(field));
            startPosition = startPosition + 1;
        }

        return startPosition;
    }

    /**
     * populates primary key values based on the primary key rules
     *
     * Override getPrimaryKeyRule() to override default rule
     *
     * @return Map representing name and value pairs of primary keys
     */
    private Map populatePrimaryKeyValuesBeforeInsert() {
        Map primaryKeyRules = getPrimaryKeyRules();
        if (primaryKeyRules == null || primaryKeyRules.size() == 0) {
            return null;//choose to let database auto generate pk
        }

        Map pkValues = new HashMap();
        for(Iterator it = primaryKeyRules.keySet().iterator(); it.hasNext();) {
            Object pkName = it.next();
            Object pkValue = primaryKeyRules.get(pkName);
            if (pkValue instanceof String) {
                String pkRule = (String)pkValue;
                if (pkRule.toUpperCase().startsWith("SQLKEY")) {
                    String sqlkey = pkRule.substring(pkRule.indexOf("=")+1);
                    pkValues.put(pkName, SqlServiceClient.retrieveObjectBySQLKey(sqlkey));
                }
                else
                if (pkRule.toUpperCase().startsWith("SQL")) {
                    String sql = pkRule.substring(pkRule.indexOf("=")+1);
                    pkValues.put(pkName, SqlServiceClient.retrieveObjectBySQL(sql));
                }
                else
                if (pkRule.toUpperCase().startsWith("SELECT")) {
                    pkValues.put(pkName, SqlServiceClient.retrieveObjectBySQL(pkRule));
                }
                else {
                    pkValues.put(pkName, pkValue);
                }
            }
            else {
                pkValues.put(pkName, pkValue);
            }
        }

        return pkValues;
    }

    private boolean isPrimaryKeyDataEmpty() {
        Map pkMap = getPrimaryKeyDataMap();
        if (pkMap == null || pkMap.size() == 0) return true;

        //make sure every pk column has data
        boolean check = false;
        Iterator it = pkMap.keySet().iterator();
        while (it.hasNext()) {
            String keyName = (String)it.next();
            Object keyValue = pkMap.get(keyName);
            if (keyValue == null) {
                check = true;
                break;
            }
        }
        return check;
    }

    /**
     * creates a backup copy of the current record data
     */
    protected void beforeSetData() {
        if (hasCopied) return;

        if (latestDbRowData == null) {
            latestDbRowData = new RowData(rowInfo, copyArray(rowData.getFields()));
        }
        else {
            latestDbRowData.setFields(copyArray(rowData.getFields()));
        }

        hasCopied = true;//only copy data when the first setData is called.
    }

    private Object[] copyArray(Object[] data) {
        if (data == null) return null;
        Object[] dest = new Object[data.length];
        System.arraycopy(data, 0, dest, 0, data.length);
        return dest;
    }

    /**
     * records those columns that are modified and sets dirty flag
     *
     * @param modifiedColumnNames
     */
    protected void afterSetData(List modifiedColumnNames) {
        if (modifiedColumnNames == null || modifiedColumnNames.size() == 0) return;
        addToModifiedColumnNames(modifiedColumnNames);
        dirty = true;
    }

    /**
     * records the column index that is modified and sets dirty flag
     *
     * @param columnIndex
     */
    protected void afterSetData(int columnIndex) {
        addToModifiedColumnNames(rowInfo.getColumnName(columnIndex));
        dirty = true;
    }

    /**
     * records the column that is modified and sets dirty flag
     *
     * @param columnName
     */
    protected void afterSetData(String columnName) {
        if (columnName == null) return;
        addToModifiedColumnNames(columnName.toUpperCase());
        dirty = true;
    }

    /**
     * do something before the record is found in database.
     */
    protected void beforeFind() {
        ;
    }

    /**
     * do something after the record is found in database.
     */
    protected void afterFind() {
        ;
    }

    /**
     * do something before the record is created in database.
     */
    protected void beforeCreate() {
        performValidationBeforeCreate();
    }

    /**
     * do something after the record is created in database.
     */
    protected void afterCreate() {
    }

    /**
     * creates the record in database and returns it.
     */
    protected ActiveRecord internal_create() {
        String createSQL = "INSERT INTO " + getTableName();

        Map pkValues = null;
        try {
            before_internal_create();

            boolean autoPopulatePrimaryKey = false;

            //prepare primary key value
            if (isPrimaryKeyDataEmpty()) {
                pkValues = populatePrimaryKeyValuesBeforeInsert();
                if (pkValues == null || pkValues.size() == 0) {
                    autoPopulatePrimaryKey = true;
                }
                else {
                    setData(pkValues);
                }
            }

            StringBuffer strBuffer = new StringBuffer();
            Map inputs = new HashMap();

            int columns = prepareInsertSQL(rowData, inputs, strBuffer, autoPopulatePrimaryKey);

            createSQL += " " + strBuffer.toString();
            log.debug("create sql = " + createSQL);

            OmniDTO returnTO =
                getSqlService().execute(inputs, DataProcessorTypes.DIRECT_SQL_STATEMENT_PROCESSOR, createSQL);

            int count = returnTO.getUpdatedRowCount();

            if (count != 1) {
                log.error("Only one record should be created, but " + count +
                          " objects were created instead.");
            }

            //populate auto-generated primary keys
            if (autoPopulatePrimaryKey) {
                long gpk = returnTO.getGeneratedKey();
                if (gpk != -1) {
                    Map pkMap = getPrimaryKeyDataMap();
                    Iterator it = pkMap.keySet().iterator();
                    if(it.hasNext()) { //only one column is allowed to be auto-generated pk
                        setData((String)it.next(), new Long(gpk));
                    }
                }
            }

            createClean();

            after_internal_create();
        }
        catch (Exception ex) {
            throw new BaseSQLException(ex);
        }

        return this;
    }

    /**
     * cleans up something before finishing create.
     *
     * This is a fresh record now.
     */
    private void createClean() {
        //remove backup copy
        hasCopied = false;
        latestDbRowData.setFields((Object[])null);
        modifiedColumns.clear();
        dirty = false;

        existInDatabase = true;
    }

    /**
     * do something before the record is deleted in database.
     */
    protected void beforeDelete() {
        performValidationBeforeDelete();
    }

    /**
     * do something after the record is deleted in database.
     */
    protected void afterDelete() {
        ;
    }

    /**
     * Do something before the internal_delete()
     *
     * For a has-one or has-many relatiosn, either the foreign key in its
     * associated records should be set to null, or the associated records
     * should be removed if it is a dependent relation.
     *
     */
    private void before_internal_delete() {
        List relations = RelationManager.getInstance().getOwnedRelations(getClass());
        if (relations == null) return;

        Iterator it = relations.iterator();
        while (it.hasNext()) {
            Relation rel = (Relation)it.next();
            String type = rel.getRelationType();
            if (Relation.HAS_MANY_TYPE.equals(type)) {
                if (rel.allowCascadeNullify()) {
                    unhookHasMany(rel);
                }
                else if (rel.allowCascadeDelete()) {
                    deleteHasMany(rel);
                }
                else if (rel.allowCascadeSimplyDelete()) {
                    deleteHasManySimply(rel);
                }
                //else no cascade effect
            }
            else if (Relation.HAS_ONE_TYPE.equals(type)) {
                if (rel.allowCascadeNullify()) {
                    unhookHasOne(rel);
                }
                else if (rel.allowCascadeDelete()) {
                    deleteHasOne(rel);
                }
                else if (rel.allowCascadeSimplyDelete()) {
                    deleteHasOneSimply(rel);
                }
                //else no cascade effect
            }
        }
    }

    /**
     * Do something after the internal_delete()
     *
     * For a belongs-to relation, decrement counter field in its parent object
     * if there is a counter field.
     */
    private void after_internal_delete() {
        List relations = RelationManager.getInstance().getOwnedRelations(getClass());
        if (relations == null) return;

        Iterator it = relations.iterator();
        while (it.hasNext()) {
            Relation rel = (Relation)it.next();
            String type = rel.getRelationType();
            if (Relation.BELONGS_TO_TYPE.equals(type)) {
                decrementCounterInParent((BelongsToRelation)rel);
            }
        }
    }

    /**
     * do something before the record is saved in database.
     */
    protected void beforeSave() {
        performValidationBeforeSave();
    }

    /**
     * do something after the record is saved in database.
     */
    protected void afterSave() {
        ;
    }

    /**
     * do something before the record is updated in database.
     */
    protected void beforeUpdate() {
        performValidationBeforeUpdate();
    }

    /**
     * do something after the record is updated in database.
     */
    protected void afterUpdate() {
        ;
    }

    /**
     * do something before the changed portion of the record is updated in database.
     */
    protected void beforeUpdateChanged() {
        performValidationBeforeUpdate();
    }

    /**
     * do something after the changed portion of the record is updated in database.
     */
    protected void afterUpdateChanged() {
        ;
    }

    /**
     * Updates changed fields in database.
     *
     * @return count of records updated.
     */
    private int internal_updateChanged() {
        if (modifiedColumns == null || modifiedColumns.size() == 0) return -1;

        before_internal_update();

        //prepare modified data map
        Map modifiedData = new HashMap();
        for (Iterator it = modifiedColumns.iterator(); it.hasNext();) {
            String field = (String)it.next();
            if (!isColumnField(field)) continue;
            Object value = rowData.getField(field);
            modifiedData.put(field, value);
        }

        int count = 0;
        String updateSQL = "UPDATE " + getTableName();

        try {
            Map inputs = new HashMap();

            int position = 1;

            //construct sets
            StringBuffer sets = new StringBuffer();
            position = prepareSetSQL(position, modifiedData, inputs, sets);

            sets = StringUtil.removeLastToken(sets, ", ");
            updateSQL += " SET " + sets.toString();

            //construct where clause
            Map conditions = rowData.getPrimaryKeyDataMap();
            if (conditions != null && conditions.size() > 0) {
                StringBuffer wheres = new StringBuffer();
                position = prepareWhereClause(position, conditions, inputs, wheres);
                updateSQL += " WHERE " + wheres.toString();
            }

            log.debug("updates sql = " + updateSQL);

            OmniDTO returnTO =
                getSqlService().execute(inputs, DataProcessorTypes.DIRECT_SQL_STATEMENT_PROCESSOR, updateSQL);

            count = returnTO.getUpdatedRowCount();

            //do some maintanance works
            updateClean();

            after_internal_update();
        }
        catch (Exception ex) {
            throw new BaseSQLException(ex);
        }

        return count;
    }

    /**
     * cleans up something before finishing update.
     *
     * This is a fresh record now.
     */
    private void updateClean() {
        //remove backup copy
        hasCopied = false;
        latestDbRowData.setFields((Object[])null);
        modifiedColumns.clear();
        dirty = false;
    }

    /**
     * Do something before the internal_create()
     *
     * For a belongs-to relation, if the parent is new or dirty, the parent
     * must be saved first. A dirty parent may have an updated foreign-key
     * value which needs to be updated in child record.
     */
    private void before_internal_create() {
        processAutoAuditCreate();

        Iterator keys = recordRelations.keySet().iterator();
        while (keys.hasNext()) {
            String key = (String)keys.next();
            RecordRelation rr = (RecordRelation)recordRelations.get(key);
            if (rr == null) continue;

            String type = rr.getRelation().getRelationType();
            if (Relation.BELONGS_TO_TYPE.equals(type)) {
            	AssociatedRecord assR = (AssociatedRecord)rr.getAssociatedData();
                if (assR != null) {
                	ActiveRecord parent = assR.getRecord();
                    if (parent != null) {
                        if (parent.isNewRecord() || parent.isDirty()) {
                            parent.save();
                        }
                        AssociationHelper.populateFKInBelongsTo(this, rr.getRelation().getMappingMap(), parent);
                    }
                }
            }
        }
    }

    /**
     * Do something for the auto-audit update fields.
     *
     * Subclass can override this method if needed.
     */
    protected void processAutoAuditCreate() {
        if (DatabaseConfig.getInstance().allowAutoAuditCreate()) {
            if (rowInfo != null) {
                String[] colNames = rowInfo.getColumnNames();
                if (colNames != null) {
                    int length = colNames.length;
                    for (int i=0; i<length; i++) {
                        String colName = colNames[i];
                        if (DatabaseConfig.getInstance().isAutoAuditCreate(colName) ||
                            DatabaseConfig.getInstance().isAutoAuditUpdate(colName)) {
                            setData(colName, getCurrentTimestamp());
                        }
                    }
                }
            }
        }
    }

    /**
     * Do something for the auto-audit update fields.
     *
     * Subclass can override this method if needed.
     */
    protected void processAutoAuditUpdate() {
        if (DatabaseConfig.getInstance().allowAutoAuditUpdate()) {
            if (rowInfo != null) {
                String[] colNames = rowInfo.getColumnNames();
                if (colNames != null) {
                    int length = colNames.length;
                    for (int i=0; i<length; i++) {
                        String colName = colNames[i];
                        if (DatabaseConfig.getInstance().isAutoAuditUpdate(colName)) {
                            setData(colName, getCurrentTimestamp());
                        }
                    }
                }
            }
        }
    }

    private Timestamp getCurrentTimestamp() {
        return new Timestamp(System.currentTimeMillis());
    }

    /**
     * Do something after the internal_create()
     *
     * For a has-one and has-many relatiosn, sets foreign-key of associated
     * objects. This is because when the owner object is newly created,
     * the associated objects may not have the foreign-key column filled.
     *
     * For a belongs-to relation, increment counter field in its parent object
     * if there is a counter field.
     */
    private void after_internal_create() {
        List relations = RelationManager.getInstance().getOwnedRelations(getClass());
        if (relations == null) return;

        Iterator it = relations.iterator();
        while (it.hasNext()) {
            Relation rel = (Relation)it.next();
            String type = rel.getRelationType();
            if (Relation.HAS_MANY_TYPE.equals(type)) {
                hookupHasMany(rel);
            }
            else if (Relation.HAS_ONE_TYPE.equals(type)) {
                hookupHasOne(rel);
            }
            else if (Relation.BELONGS_TO_TYPE.equals(type)) {
                incrementCounterInParent((BelongsToRelation)rel);
            }
        }
    }

    /**
     * Sets up foreign-key link in child record. This is equvilent to execute
     * this sql statement:
     *
     * <blockquote>update items set order_id = 1 where id = 10</blockquote>
     *
     * @param rel relation
     */
    private void hookupHasOne(Relation rel) {
        //set FK in otherRecord--the associated object, based on owner's PK data
        if (rel == null) return;

        RecordRelation rr = getRecordRelation(rel.getAssociation());
        AssociatedRecord assR = (AssociatedRecord)rr.getAssociatedData();
        if (assR != null) {
        	ActiveRecord target = assR.getRecord();
            if (target != null) {
            	Map fkData = rr.getFKDataMapForOther();
                if (fkData == null) return;
                if (target.isNewRecord() || fkChangable(target, fkData)) {
                    target.setData(fkData);
                }

                if (target.isDirty()) {
                    target.save();
                }
            }
        }
    }

    /**
     * Sets up foreign-key link in child records. This is equvilent to execute
     * this sql statement for each child record:
     *
     * <blockquote>update items set order_id = 1 where id = 10</blockquote>
     *
     * @param rel relation
     */
    private void hookupHasMany(Relation rel) {
        if (rel == null) return;

        RecordRelation rr = getRecordRelation(rel.getAssociation());
        AssociatedRecords assRs = (AssociatedRecords)rr.getAssociatedData();
        if (assRs != null) {
        	List list = assRs.getRecords();
            if (list != null) {
            	Map fkData = rr.getFKDataMapForOther();
                if (fkData == null) return;
                for (int i=0; i<list.size(); i++) {
                    ActiveRecord target = (ActiveRecord)list.get(i);
                    if (target != null) {
                        if (target.isNewRecord() || fkChangable(target, fkData)) {
                            target.setData(fkData);
                        }

                        if (target.isDirty()) {
                        	target.save();
                        }
                    }
                }
            }
        }
    }

    private void deleteHasOne(Relation rel) {
        //set FK in otherRecord--the associated object, based on owner's PK data
        if (rel == null) return;
        AssociatedRecord ar = associated(rel.getAssociation(), true);
        if (ar != null) {
            ActiveRecord target = ar.getRecord();
            if (target != null) {
                target.delete();
            }
        }
    }

    //It is important to delete each child individually, because each may hook
    //with other records.
    //For example:
    //delete from items where order_id = 1
    private void deleteHasMany(Relation rel) {
        if (rel == null) return;
        AssociatedRecords ars = allAssociated(rel.getAssociation(), true);
        //forced refresh is required otherwise it returns ars.size = 0;
        if (ars != null) {
            List list = ars.getRecords();
            if (list != null) {
                for (int i=0; i<list.size(); i++) {
                    ActiveRecord target = (ActiveRecord)list.get(i);
                    if (target != null) {
                        target.delete();
                    }
                }
            }
        }
    }

    /**
     * Deletes child record without triggering callbacks. The example sql
     * statement:
     *
     * <blockquote>delete from items where order_id = 1</blockquote>
     *
     * @param rel relation
     */
    private void deleteHasOneSimply(Relation rel) {
        deleteHasManySimply(rel);
    }

    /**
     * Deletes child records without triggering callbacks. The example sql
     * statement:
     *
     * <blockquote>delete from items where order_id = 1</blockquote>
     *
     * @param rel relation
     */
    private void deleteHasManySimply(Relation rel) {
        if (rel == null) return;

        ActiveRecord childHome = ActiveRecordUtil.getHomeInstance(rel.getTargetClass());
        String childTable = childHome.getTableName();

        Object[] lhsFlds = rel.getLeftSideMappingItems();
        Object[] rhsFlds = rel.getRightSideMappingItems();

        StringBuffer sb = new StringBuffer();
        sb.append("DELETE FROM ").append(childTable);

        StringBuffer whereSB = new StringBuffer();
        Map inputs = new HashMap();
        int size = lhsFlds.length;
        for (int i=0; i<size; i++) {
            String fkFld = (String)rhsFlds[i];
            String pkFld = (String)lhsFlds[i];
            Object fkData = getField(pkFld);
            whereSB.append(fkFld).append(" = ").append("? AND ");
            inputs.put((i+1)+"", fkData);
        }

        String whereStr = StringUtil.removeLastToken(whereSB.toString(), "AND ");

        String deleteSQL = sb.append(" WHERE ").append(whereStr).toString();
        log.debug("deleteHasManySimply deleteSQL: " + deleteSQL);

        TableGateway.deleteBySQL(deleteSQL, inputs);
    }

    /**
     * Removes foreign-key link in child records. The example sql
     * statement:
     *
     * <blockquote>update items set order_id = null where order_id = 1</blockquote>
     *
     * If foreign-key column is not nullable, an exception will be thrown.
     *
     * @param rel relation
     */
    private void unhookHasOne(Relation rel) {
        unhookHasMany(rel);
    }

    /**
     * Removes foreign-key link in child records. The example sql
     * statement:
     *
     * <blockquote>update items set order_id = null where order_id = 1</blockquote>
     *
     * If foreign-key column is not nullable, an exception will be thrown.
     *
     * @param rel relation
     */
    private void unhookHasMany(Relation rel) {
        //set FK in children, based on owner's PK data
        if (rel == null) return;

        //need to verify the foreign key field is nullable.
        ActiveRecord childHome = ActiveRecordUtil.getHomeInstance(rel.getTargetClass());
        String childTable = childHome.getTableName();

        Object[] lhsFlds = rel.getLeftSideMappingItems();
        Object[] rhsFlds = rel.getRightSideMappingItems();

        StringBuffer sb = new StringBuffer();
        sb.append("UPDATE ").append(childTable).append(" SET ");

        StringBuffer setSB = new StringBuffer();
        StringBuffer whereSB = new StringBuffer();

        Map inputs = new HashMap();
        int size = lhsFlds.length;
        for (int i=0; i<size; i++) {
            String fkFld = (String)rhsFlds[i];
            if (childHome.isRequiredColumn(fkFld)) {
                throw new GenericException("Column " + fkFld + " in table " + childTable + " cannot be nullified.");
            }
            String pkFld = (String)lhsFlds[i];
            Object fkData = getField(pkFld);
            setSB.append(fkFld).append(" = NULL, ");
            whereSB.append(fkFld).append(" = ").append("? AND ");
            inputs.put((i+1)+"", fkData);
        }

        String setStr = StringUtil.removeLastToken(setSB.toString(), ", ");
        String whereStr = StringUtil.removeLastToken(whereSB.toString(), "AND ");

        String updateSQL = sb.append(setStr).append(" WHERE ").append(whereStr).toString();

        TableGateway.updateBySQL(updateSQL, inputs);
    }

    void incrementCounterInParent(BelongsToRelation btr) {
        if (btr == null || isNewRecord()) return;

        if (btr.hasCounterCache()) {
            ActiveRecord parentRecord = associated(btr.getAssociation()).getRecord();
            if (parentRecord != null) {
                parentRecord.incrementCounter(btr.getCounterCacheName());
            }
        }
    }

    void decrementCounterInParent(BelongsToRelation btr) {
        if (btr == null || isNewRecord()) return;
        if (btr.hasCounterCache()) {
            ActiveRecord parentRecord = associated(btr.getAssociation()).getRecord();

            if (parentRecord != null) {
                parentRecord.decrementCounter(btr.getCounterCacheName());
            }
        }
    }

    /**
     * Do something before the internal_update()
     *
     * For a belongs-to relation, if the parent is new or dirty, the parent
     * must be saved first. A dirty parent may have an updated foreign-key
     * value which needs to be updated in child record.
     */
    private void before_internal_update() {
        processAutoAuditUpdate();

        Iterator keys = recordRelations.keySet().iterator();
        while (keys.hasNext()) {
            String key = (String)keys.next();
            RecordRelation rr = (RecordRelation)recordRelations.get(key);
            if (rr == null) continue;

            String type = rr.getRelation().getRelationType();
            if (Relation.BELONGS_TO_TYPE.equals(type)) {
            	AssociatedRecord assR = (AssociatedRecord)rr.getAssociatedData();
                if (assR != null) {
                	ActiveRecord parent = assR.getRecord();
                    if (parent != null) {
                        if (parent.isNewRecord() || parent.isDirty()) {
                            parent.save();
                        }
                        AssociationHelper.populateFKInBelongsTo(this, rr.getRelation().getMappingMap(), parent);
                    }
                }
            }
        }
    }

    /**
     * Do something after the internal_update()
     *
     * For a has-one and has-many relatiosn, sets foreign-key of associated
     * objects. This is because the associated objects may not have the
     * foreign-key column filled or the associated objects may be dirty.
     */
    private void after_internal_update() {

        //do some maintanance works
        updateClean();

        Iterator keys = recordRelations.keySet().iterator();
        while (keys.hasNext()) {
            String key = (String)keys.next();
            RecordRelation rr = (RecordRelation)recordRelations.get(key);
            if (rr == null) continue;

            String type = rr.getRelation().getRelationType();
            if (Relation.HAS_MANY_TYPE.equals(type)) {
                updateAssociatedHasMany(rr);
            }
            else if (Relation.HAS_ONE_TYPE.equals(type)) {
                updateAssociatedHasOne(rr);
            }
        }
    }

    /**
     * updates associated record if it has been modified.
     *
     * @param rr RecordRelation
     */
    private void updateAssociatedHasOne(RecordRelation rr) {
        if (rr == null) return;

        AssociatedRecord assR = (AssociatedRecord)rr.getAssociatedData();
        if (assR != null) {
            ActiveRecord target = assR.getRecord();
            if (target != null) {
                Map fkData = rr.getFKDataMapForOther();
                if (target.isNewRecord() || fkChangable(target, fkData)) {
                    target.setData(fkData);
                }

                if (target.isDirty()) {
                    target.save();
                }
            }
        }
    }

    /**
     * updates associated records if they have been modified.
     *
     * @param rr RecordRelation
     */
    private void updateAssociatedHasMany(RecordRelation rr) {
        //set FK in children, based on owner's PK data
        if (rr == null) return;

        AssociatedRecords assRs = (AssociatedRecords)rr.getAssociatedData();
        if (assRs != null) {
            List list = assRs.getRecords();
            if (list != null) {
                Map fkData = rr.getFKDataMapForOther();
                for (int i=0; i<list.size(); i++) {
                    ActiveRecord target = (ActiveRecord)list.get(i);
                    if (target != null) {
                    	if (target.isNewRecord() || fkChangable(target, fkData)) {
                            target.setData(fkData);
                        }

                        if (target.isDirty()) {
                        	target.save();
                        }
                    }
                }
            }
        }
    }

    private boolean fkChangable(ActiveRecord target, Map fkData) {
		boolean status = false;
    	Iterator it = fkData.keySet().iterator();
    	while(it.hasNext()) {
    		String keyColumn = (String)it.next();
    		Object value = fkData.get(keyColumn);
    		Object targetValue = target.getField(keyColumn);
    		if (value != null) {
    			if (target != null) {
	    			if (!value.toString().equals(targetValue.toString())) {
	    				status = true;
	    				break;
					}
    			}
    			else {
    				status = true;
    				break;
    			}
    		}
    		else {
    			if (targetValue != null) {
    				status = true;
    				break;
    			}
    		}
    	}
		return status;
    }

    private void addToModifiedColumnNames(String name) {
        if (name == null) return;
        name = name.toUpperCase();
        if (!modifiedColumns.contains(name)) modifiedColumns.add(name);
    }

    private void addToModifiedColumnNames(List names) {
        if (names == null) return;
        Iterator it = names.iterator();
        while(it.hasNext()) {
            String name = (String)it.next();
            addToModifiedColumnNames(name);
        }
    }

    /**
     * <p>Acts as a certain type in a category. </p>
     *
     * <p>This method adds a hasMany association with the target in the owner
     * class, and a belongsTo association in the target class. </p>
     *
     * <p>The <tt>target</tt> class is the center class of the category.</p>
     *
     * <pre>
     * Examples:
     *   owner class  => Image, File, Post
     *   category     => taggable
     *   target class => Tagging
     * </pre>
     *
     * @param category the category this model performs
     * @param target the associated class
     */
    public void actAsInCategory(String category, Class target) {
        String type = ActiveRecordUtil.getModelName(getClass());
        actAsInCategory(type, category, target);
    }

    /**
     * <p>Acts as a certain type in a category. </p>
     *
     * <p>This method adds a hasMany association with the target in the owner
     * class, and a belongsTo association in the target class. </p>
     *
     * <p>This method assumes that you use "id" as primary key in the owner entity.</p>
     *
     * <pre>
     * Examples:
     *   owner class  => Image, File, Post
     *   type         => image, file, post
     *   category     => taggable
     *   target class => Tagging
     * </pre>
     *
     * @param type the specific type this entity represents
     * @param category the category this entity performs
     * @param target the associated class
     */
    public void actAsInCategory(String type, String category, Class target) {
        //make sure category center is loaded first
        RelationManager.getInstance().registerRelations(target);

        Category categoryInstance = RelationManager.getInstance().getCategory(category);
        if (categoryInstance == null) {
            throw new UnregisteredCategoryException(category);
        }
        String idField = categoryInstance.getIdField();
        String typeField = categoryInstance.getTypeField();
        String cTableName = ActiveRecordUtil.getTableName(target);
        String associationType = Relation.HAS_MANY_TYPE;

        String bcProperties = ActiveRecordConstants.key_mapping + ": id=" + idField + "; " +
                              ActiveRecordConstants.key_conditions_sql + ": " + cTableName + "." + typeField + "='" + type + "'; cascade: delete";
        String cbProperties = ActiveRecordConstants.key_mapping + ": " + idField + "=id; ";
        actAsInCategory(type, category, associationType, target, bcProperties, cbProperties);
    }

    /**
     * <p>Acts as a certain type in a category. </p>
     *
     * <p>This method adds a hasMany (or hasOne) association with the target
     * in the owner class, and a belongsTo association in the target class. The
     * target class is the center of the category.</p>
     *
     * <p>Assuming owner class is B, target class is C,
     * bcProperties is join properties from B to C,
     * cbProperties is join properties from C to B.</p>
     *
     * <pre>
     * Examples:
     *   owner class  => Image, File, Post
     *   type         => image, file, post
     *   category     => taggable
     *   target class => Tagging
     * </pre>
     *
     * @param type the specific type this entity represents in the category
     * @param category the category this entity performs
     * @param relationType either has-many or has-one
     * @param target the associated class
     * @param bcProperties properties of the has-many or has-one association from owner to target
     * @param cbProperties properties of the belongs-to association from target to owner
     */
    public void actAsInCategory(String type, String category, String relationType,
                Class target, String bcProperties, String cbProperties) {
        //make sure category center is loaded first
        RelationManager.getInstance().registerRelations(target);

        //register the entity in category:
        Category categoryInstance = RelationManager.getInstance().getCategory(category);
        if (categoryInstance == null) {
            throw new UnregisteredCategoryException(category);
        }
        String idField = categoryInstance.getIdField();
        String typeField = categoryInstance.getTypeField();
        String cTableName = ActiveRecordUtil.getTableName(target);
        if (type == null) type = ActiveRecordUtil.getModelName(getClass());
        categoryInstance.addEntity(type, ActiveRecordUtil.getModelName(getClass()));

        String throughTypeCondition = ActiveRecordConstants.key_conditions_sql + ": " + cTableName + "." + typeField + "='" + type + "'";

        if (bcProperties == null) {
            bcProperties = ActiveRecordConstants.key_mapping + ": id=" + idField + "; " +
                           throughTypeCondition + "; cascade: delete";
        }
        else {
            if (bcProperties.indexOf(ActiveRecordConstants.key_conditions_sql) == -1) {
                bcProperties = throughTypeCondition + "; " + bcProperties;
            }

            if (bcProperties.indexOf(ActiveRecordConstants.key_mapping) == -1) {
                bcProperties = ActiveRecordConstants.key_mapping + ": id=" + idField + "; " + bcProperties;
            }

            if (bcProperties.indexOf(ActiveRecordConstants.key_cascade) == -1) {
                bcProperties = ActiveRecordConstants.key_cascade + ": delete" + "; " + bcProperties;
            }
        }

        if (Relation.HAS_ONE_TYPE.equals(relationType)) {
            hasOne(target, bcProperties);
        }
        else
        if (Relation.HAS_MANY_TYPE.equals(relationType)) {
            hasMany(target, bcProperties);
        }
        else {
            throw new UndefinedRelationException("Relation type " + relationType + " is not allowed in actAsInCategory().");
        }

        //add the following associations in target:
        String cbMappingProperty = ActiveRecordConstants.key_mapping + ": " + idField + "=id; ";
        if (cbProperties == null) {
            cbProperties = cbMappingProperty;
        }
        else {
            if (cbProperties.indexOf(ActiveRecordConstants.key_mapping) == -1) {
                cbProperties = cbMappingProperty + "; " + cbProperties;
            }
        }
        ActiveRecord targetHome = ActiveRecordUtil.getHomeInstance(target);
        targetHome.belongsTo(getClass(), cbProperties);
    }

    /**
     * Returns a RecordRelation related to the target model.
     *
     * The <tt>associationId</tt> is the name of the relation defined in the class.
     *
     * @param associationId association id
     * @return a RecordRelation related to the target model
     */
    public RecordRelation getRecordRelation(String associationId) {
        if (associationId == null) throw new IllegalArgumentException("association name is empty.");

        //find the relationship type between this and model
        associationId = associationId.toLowerCase();
        RecordRelation rr = (RecordRelation)recordRelations.get(associationId);

        if (rr == null) {
            rr = RelationManager.getInstance().createRecordRelation(this, associationId);
            recordRelations.put(associationId, rr);
        }

        return rr;
    }

    /**
     * Sets a RecordRelation related to the target model.
     *
     * @param associationId association id
     * @param rr            RecordRelation related to the target model
     */
    public void setRecordRelation(String associationId, RecordRelation rr) {
    	recordRelations.put(associationId, rr);
    }

    /**
     * Sets fields to be protected.
     *
     * @param fields A string of field names separated by comma.
     */
    protected void setProtectedFields(String fields) {
        if (fields != null) {
            protectedColumns.addAll(Converters.convertStringToList(fields.toUpperCase()));
        }
    }

    /**
     * Returns true if the field is a protected field.
     *
     * @param field
     * @return boolean true if the field is a protected field.
     */
    private boolean isProtectedField(String field) {
        boolean pted = false;
        if (field != null) {
            pted = protectedColumns.contains(field.toUpperCase());
        }
        return pted;
    }

    private Map filterProtectedFields(Map dataMap) {
        if (dataMap == null) return dataMap;

        Map newDataMap = new HashMap();
        Iterator it = dataMap.keySet().iterator();
        while(it.hasNext()) {
            String key = (String)it.next();
            if (isProtectedField(key)) continue;
            newDataMap.put(key, dataMap.get(key));
        }
        return newDataMap;
    }

    /**
     * Sets fields to be extra.
     *
     * @param fields A string of field names separated by comma.
     */
    protected void setExtraFields(String fields) {
        if (fields != null) {
            extraFields.addAll(Converters.convertStringToList(fields.toUpperCase()));
        }
    }

    private Object getExtraFieldData(String fieldName) {
        if (fieldName == null) return null;
        return extraFieldsMap.get(fieldName.toUpperCase());
    }

    private void setExtraFieldData(String fieldName, Object data) {
        if (fieldName == null) return;
        extraFieldsMap.put(fieldName.toUpperCase(), data);
    }

    private Map getExtraFieldData(List fieldNames) {
        if (fieldNames == null) return null;
        Map fieldData = new HashMap();
        Iterator it = fieldNames.iterator();
        while(it.hasNext()) {
            String field = (String)it.next();
            if (isExtraField(field)) {
                fieldData.put(field, getExtraFieldData(field));
            }
        }
        return fieldData;
    }

    private List setExtraFieldData(Map data) {
        if (data == null) return null;
        List changedFields = new ArrayList();
        Iterator it = data.keySet().iterator();
        while(it.hasNext()) {
            String field = (String)it.next();
            if (isExtraField(field)) {
                setExtraFieldData(field, data.get(field));
                changedFields.add(field.toUpperCase());
            }
        }
        return changedFields;
    }

    /**
     *
     * DELETE related
     *
     */

    /**
     * Deletes all the records that satisfy the sql.
     *
     * @param sql    a key to a sql string
     * @return int number of records deleted
     */
    public static int deleteBySQL(String sql) {
        return TableGateway.deleteBySQL(sql);
    }

    /**
     * Deletes all the records that satisfy the sql.
     *
     * The inputs is a map of name and value pairs related to the sql.
     *
     * @param sql    a key to a sql string
     * @param inputs a map of name and value pairs
     * @return int number of records deleted
     */
    public static int deleteBySQL(String sql, Map inputs) {
        return TableGateway.deleteBySQL(sql, inputs);
    }

    /**
     * Deletes all the records that satisfy the sql specified by the <tt>sqlKey</tt>.
     *
     * @param sqlKey a key to a sql string
     * @return int number of records deleted
     */
    public static int deleteBySQLKey(String sqlKey) {
        return TableGateway.deleteBySQLKey(sqlKey);
    }

    /**
     * Deletes all the records that satisfy the sql specified by the <tt>sqlKey</tt>.
     *
     * The inputs is a map of name and value pairs related to the sql.
     *
     * @param sqlKey a key to a sql string
     * @param inputs a map of name and value pairs
     * @return int number of records deleted
     */
    public static int deleteBySQLKey(String sqlKey, Map inputs) {
        return TableGateway.deleteBySQLKey(sqlKey, inputs);
    }


    /**
     *
     * UPDATE related
     *
     */

    /**
     * Updates all the records that satisfy the sql.
     *
     * @param sql A valid sql string
     * @return int number of records updated
     */
    public static int updateBySQL(String sql) {
        return TableGateway.updateBySQL(sql);
    }

    /**
     * Updates all the records that satisfy the sql.
     *
     * @param sql A valid sql string
     * @param inputs a map of name and value pairs
     * @return int number of records updated
     */
    public static int updateBySQL(String sql, Map inputs) {
        return TableGateway.updateBySQL(sql, inputs);
    }

    /**
     * Updates all the records that satisfy the sql specified by <tt>sqlKey</tt>.
     *
     * @param sqlKey a key to a sql string.
     * @return int number of records updated
     */
    public static int updateBySQLKey(String sqlKey) {
        return TableGateway.updateBySQLKey(sqlKey);
    }

    /**
     * Updates all the records that satisfy the sql specified by <tt>sqlKey</tt>.
     *
     * @param sqlKey a key to a sql string
     * @param inputs a map of name and value pairs
     * @return int number of records updated
     */
    public static int updateBySQLKey(String sqlKey, Map inputs) {
        return TableGateway.updateBySQLKey(sqlKey, inputs);
    }



    /**
     * Returns the <tt>ValidationResults</tt> instance of this record.
     *
     * @return ValidationResults
     */
    public ValidationResults getValidationResults() {
        return errors;
    }

    /**
     * Returns ture if the record has no error.
     */
    public boolean isValid() {
        return !errors.failed();
    }

    private void clearValidationResults() {
        errors.clear();
    }

    private void performValidationBeforeCreate() {
        clearValidationResults();

        validatesRecordBeforeCreate();

        if (errors.failed()) {
            throw new RecordValidationException(errors);
        }
    }

    private void performValidationBeforeUpdate() {
        clearValidationResults();

        validatesRecordBeforeUpdate();

        if (errors.failed()) {
            throw new RecordValidationException(errors);
        }
    }

    private void performValidationBeforeSave() {
        clearValidationResults();

        validatesRecordBeforeSave();

        if (errors.failed()) {
            throw new RecordValidationException(errors);
        }
    }

    private void performValidationBeforeDelete() {
        clearValidationResults();

        validatesRecordBeforeDelete();

        if (errors.failed()) {
            throw new RecordValidationException(errors);
        }
    }

    /**
     * This is an empty method. Subclass must override this method in order
     * to provide meaningful validation.
     *
     * This is the default implmentation of validation related to creating,
     * updating, and saving a record.
     */
    public void validatesRecord() {
        ;
    }

    /**
     * This delegates to validatesRecord(). Subclass need to override this
     * method in order to provide a meaningful validation.
     */
    public void validatesRecordBeforeCreate() {
        validatesRecord();
    }

    /**
     * This delegates to validatesRecord(). Subclass need to override this
     * method in order to provide a meaningful validation.
     */
    public void validatesRecordBeforeUpdate() {
        validatesRecord();
    }

    /**
     * This delegates to validatesRecord(). Subclass need to override this
     * method in order to provide a meaningful validation.
     */
    public void validatesRecordBeforeSave() {
        validatesRecord();
    }

    /**
     * The default implementation actually does nothing. Subclass need to
     * override this method in order to provide a meaningful validation.
     */
    public void validatesRecordBeforeDelete() {
        ;
    }

    /**
     * Returns an instance of Calculations.
     *
     * Subclass must override this method if a different calculator is used.
     *
     * @return Calculations object
     */
    public Calculator getCalculator() {
        return new Calculator(this);
    }

    /**
     * Returns an instance of Validators.
     *
     * Subclass must override this method if a different validator is used.
     *
     * @return Validators object
     */
    public ModelValidators validators() {
        return (validators != null)?validators:(new ModelValidators(this));
    }
    
    

    /************************************************************************
     *
     * The following static methods declaration should be implemented in 
     * subclass by load-time method injection.
     *
     ************************************************************************/
    
    private static String UO = "This method should be invoked from a subclass of ActiveRecord.";

    /**
     *
     * CREATE related
     *
     */
    
    /**
     * Creates a new instance.
     */
    public static ActiveRecord newRecord() {
        throw new UnsupportedOperationException(UO);
    }

    /**
     *
     * FIND related
     *
     */

    /**
     * Finds the record with the given id, assuming ID is a column.
     *
     * If there is no column name like "ID", an exception will be thrown.
     *
     * @param id the id of the record
     * @return the ActiveRecord associated with the <tt>id</tt>
     */
    public static ActiveRecord findById(Object id) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records that satisfy the SQL query.</p>
     *
     * @param sql       a valid SQL query string
     * @return a list of ActiveRecord objects
     */
    public static List findAllBySQL(String sql) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records that satisfy the SQL query.</p>
     *
     * @param sql       a valid SQL query string
     * @param inputs    a map of name and value pairs
     * @return a list of ActiveRecord objects
     */
    public static List findAllBySQL(String sql, Map inputs) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records that satisfy the sql corresponding to the
     * sql key. </p>
     *
     * @param sqlKey    a key to a sql string defined in <tt>sql.properties</tt> file
     * @return a list of ActiveRecord objects
     */
    public static List findAllBySQLKey(String sqlKey) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records that satisfy the sql corresponding to the
     * sql key. </p>
     *
     * @param sqlKey    a key to a sql string defined in <tt>sql.properties</tt> file
     * @param inputs    a map of name and value pairs
     * @return a list of ActiveRecord objects
     */
    public static List findAllBySQLKey(String sqlKey, Map inputs) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the first record that satisfy the conditions.</p>
     *
     * <p>This is a dynamic finder method.
     * See top of this class for dynamic finder examples.</p>
     *
     * @param columns  a string of column names linked by "_and_".
     * @param values   an Object[] array
     * @return  ActiveRecord
     */
    public static ActiveRecord findFirstBy(String columns, Object[] values) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the last record that satisfy the conditions.</p>
     *
     * <p>This is a dynamic finder method.
     * See top of this class for dynamic finder examples.</p>
     *
     * @param columns  a string of column names linked by "_and_".
     * @param values   an Object[] array
     * @return  ActiveRecord
     */
    public static ActiveRecord findLastBy(String columns, Object[] values) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records that satisfy the conditions.</p>
     *
     * <p>This is a dynamic finder method.
     * See top of this class for dynamic finder examples.</p>
     *
     * @param columns  a string of column names linked by "_and_".
     * @param values   an Object[] array
     * @return  List of ActiveRecord objects
     */
    public static List findAllBy(String columns, Object[] values) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records that satisfy the conditions.</p>
     *
     * <p>This is a dynamic finder method.
     * See top of this class for dynamic finder examples.</p>
     *
     * @param columns  a string of column names linked by "_and_".
     * @param values   an Object[] array
     * @param options  a map of options
     * @return  List of ActiveRecord objects
     */
    public static List findAllBy(String columns, Object[] values, Map options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records that satisfy the conditions.</p>
     *
     * <p>This is a dynamic finder method.
     * See top of this class for dynamic finder examples.</p>
     *
     * @param columns  a string of column names linked by "_and_".
     * @param values   an Object[] array
     * @param options  a string of options
     * @return  List of ActiveRecord objects
     */
    public static List findAllBy(String columns, Object[] values, String options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records of a table.</p>
     *
     * @return a list of ActiveRecord objects
     */
    public static List findAll() {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records that satisfy the conditions.</p>
     *
     * <p>See top of this class for <tt>conditions</tt> examples.</p>
     *
     * @param conditions a map of column name and value pairs
     * @return a list of ActiveRecord objects
     */
    public static List findAll(Map conditions) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records that satisfy the conditions and options.</p>
     *
     * <p>See top of this class for <tt>conditions</tt> and <tt>options</tt> examples.</p>
     *
     * @param conditions a map of column name and value pairs
     * @param options a map of options
     * @return a list of ActiveRecord objects
     */
    public static List findAll(Map conditions, Map options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records that satisfy the conditions and options.</p>
     *
     * <p>See top of this class for <tt>conditions</tt> and <tt>options</tt> examples.</p>
     *
     * @param conditions a map of column name and value pairs
     * @param options a string of options
     * @return a list of ActiveRecord objects
     */
    public static List findAll(Map conditions, String options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records that satisfy the conditions.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt> examples.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @return a list of ActiveRecord objects
     */
    public static List findAll(String conditionsSQL) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records that satisfy the conditions and options.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt> and <tt>options</tt> examples.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @param options a string of options.
     * @return a list of ActiveRecord objects
     */
    public static List findAll(String conditionsSQL, String options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records that satisfy the conditions.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt> and
     * <tt>conditionsSQLData</tt> examples.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @param conditionsSQLData a data map for dynamic attributes in <tt>conditionsSQL</tt>
     * @return a list of ActiveRecord objects
     */
    public static List findAll(String conditionsSQL, Map conditionsSQLData) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records that satisfy the conditions and options.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt>,
     * <tt>conditionsSQLData</tt> and <tt>options</tt> examples.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @param conditionsSQLData a data map for dynamic attributes in <tt>conditionsSQL</tt>
     * @param options a map of options.
     * @return a list of ActiveRecord objects
     */
    public static List findAll(String conditionsSQL, Map conditionsSQLData, Map options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds all the records that satisfy the conditions and options.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt>,
     * <tt>conditionsSQLData</tt> and <tt>options</tt> examples.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @param conditionsSQLData a data map for dynamic attributes in <tt>conditionsSQL</tt>
     * @param options a string of options.
     * @return a list of ActiveRecord objects
     */
    public static List findAll(String conditionsSQL, Map conditionsSQLData, String options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the first record of a table.</p>
     *
     * @return the first ActiveRecord found
     */
    public static ActiveRecord findFirst() {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the first record that satisfy the conditions.</p>
     *
     * <p>See top of this class for <tt>conditions</tt> examples.</p>
     *
     * @param conditions a map of column name and value pairs
     * @return the first ActiveRecord found
     */
    public static ActiveRecord findFirst(Map conditions) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the first record that satisfy the conditions and options.</p>
     *
     * <p>See top of this class for <tt>conditions</tt> and <tt>options</tt> examples.</p>
     *
     * @param conditions a map of column name and value pairs
     * @param options a map of options
     * @return the first ActiveRecord found
     */
    public static ActiveRecord findFirst(Map conditions, Map options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the first record that satisfy the conditions and options.</p>
     *
     * <p>See top of this class for <tt>conditions</tt> and <tt>options</tt> examples.</p>
     *
     * @param conditions a map of column name and value pairs
     * @param options a string of options
     * @return the first ActiveRecord found
     */
    public static ActiveRecord findFirst(Map conditions, String options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the first record that satisfy the conditions.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt> examples.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @return the first ActiveRecord found
     */
    public static ActiveRecord findFirst(String conditionsSQL) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the first record that satisfy the conditions and options.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt> and <tt>options</tt> examples.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @param options a string of options.
     * @return the first ActiveRecord found
     */
    public static ActiveRecord findFirst(String conditionsSQL, String options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the first record that satisfy the conditions.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt> and
     * <tt>conditionsSQLData</tt> examples.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @param conditionsSQLData a data map for dynamic attributes in <tt>conditionsSQL</tt>
     * @return the first ActiveRecord found
     */
    public static ActiveRecord findFirst(String conditionsSQL, Map conditionsSQLData) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the first record that satisfy the conditions and options.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt>,
     * <tt>conditionsSQLData</tt> and <tt>options</tt> examples.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @param conditionsSQLData a data map for dynamic attributes in <tt>conditionsSQL</tt>
     * @param options a map of options.
     * @return the first ActiveRecord found
     */
    public static ActiveRecord findFirst(String conditionsSQL, Map conditionsSQLData, Map options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the first record that satisfy the conditions and options.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt>,
     * <tt>conditionsSQLData</tt> and <tt>options</tt> examples.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @param conditionsSQLData a data map for dynamic attributes in <tt>conditionsSQL</tt>
     * @param options a string of options.
     * @return the first ActiveRecord found
     */
    public static ActiveRecord findFirst(String conditionsSQL, Map conditionsSQLData, String options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the last record of a table.</p>
     *
     * @return the last ActiveRecord found
     */
    public static ActiveRecord findLast() {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the last record that satisfy the conditions.</p>
     *
     * <p>See top of this class for <tt>conditions</tt> examples.</p>
     *
     * @param conditions a map of column name and value pairs
     * @return the last ActiveRecord found
     */
    public static ActiveRecord findLast(Map conditions) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the last record that satisfy the conditions and options.</p>
     *
     * <p>See top of this class for <tt>conditions</tt> and <tt>options</tt> examples.</p>
     *
     * @param conditions a map of column name and value pairs
     * @param options a map of options
     * @return the last ActiveRecord found
     */
    public static ActiveRecord findLast(Map conditions, Map options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the last record that satisfy the conditions and options.</p>
     *
     * <p>See top of this class for <tt>conditions</tt> and <tt>options</tt> examples.</p>
     *
     * @param conditions a map of column name and value pairs
     * @param options a string of options
     * @return the last ActiveRecord found
     */
    public static ActiveRecord findLast(Map conditions, String options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the last record that satisfy the conditions.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt> examples.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @return the last ActiveRecord found
     */
    public static ActiveRecord findLast(String conditionsSQL) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the last record that satisfy the conditions and options.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt> and <tt>options</tt> examples.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @param options a string of options.
     * @return the last ActiveRecord found
     */
    public static ActiveRecord findLast(String conditionsSQL, String options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the last record that satisfy the conditions.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt> and
     * <tt>conditionsSQLData</tt> examples.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @param conditionsSQLData a data map for dynamic attributes in <tt>conditionsSQL</tt>
     * @return the last ActiveRecord found
     */
    public static ActiveRecord findLast(String conditionsSQL, Map conditionsSQLData) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the last record that satisfy the conditions and options.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt>,
     * <tt>conditionsSQLData</tt> and <tt>options</tt> examples.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @param conditionsSQLData a data map for dynamic attributes in <tt>conditionsSQL</tt>
     * @param options a map of options.
     * @return the last ActiveRecord found
     */
    public static ActiveRecord findLast(String conditionsSQL, Map conditionsSQLData, Map options) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Finds the last record that satisfy the conditions and options.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt>,
     * <tt>conditionsSQLData</tt> and <tt>options</tt> examples.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @param conditionsSQLData a data map for dynamic attributes in <tt>conditionsSQL</tt>
     * @param options a string of options.
     * @return the last ActiveRecord found
     */
    public static ActiveRecord findLast(String conditionsSQL, Map conditionsSQLData, String options) {
        throw new UnsupportedOperationException(UO);
    }

    
    /**
     *
     * UPDATE related
     *
     */
    
    /**
     * <p>Updates all the records of a table.</p>
     *
     * <p>This method sends a single SQL UPDATE statement to database. No
     * callbacks are triggered. If callbacks are necessary for handling
     * associations, use <tt>findAll</tt> method to retrieve a list of records
     * and then call each record's <tt>update()</tt> method.</p>
     *
     * <p><tt>fieldData</tt> map is used to construct SET clause of the
     * generated SQL. It consists of column name and its value pairs in the map.
     * Primary key column and read-only columns are not updatable.</p>
     *
     * @param fieldData a map of field name and its data to be set on any records
     * @return int number of records updated
     */
    public static int updateAll(Map fieldData) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Updates all the records that satisfy the conditions.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt> examples.</p>
     *
     * <p>This method sends a single SQL UPDATE statement to database. No
     * callbacks are triggered. If callbacks are necessary for handling
     * associations, use <tt>findAll</tt> method to retrieve a list of records
     * and then call each record's <tt>update()</tt> method.</p>
     *
     * <p><tt>fieldData</tt> map is used to construct SET clause of the
     * generated SQL. It consists of column name and its value pairs in the map.
     * Primary key column and read-only columns are not updatable.</p>
     *
     * @param fieldData a map of field name and its data to be set on any
     *          records that satisfy the conditions.
     * @param conditionsSQL a SQL fragment string
     * @return int number of records updated
     */
    public static int updateAll(Map fieldData, String conditionsSQL) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Updates all the records that satisfy the conditions.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt>
     * and <tt>conditionsSQLData</tt> examples.</p>
     *
     * <p>This method sends a single SQL UPDATE statement to database. No
     * callbacks are triggered. If callbacks are necessary for handling
     * associations, use <tt>findAll</tt> method to retrieve a list of records
     * and then call each record's <tt>update()</tt> method.</p>
     *
     * <p><tt>fieldData</tt> map is used to construct SET clause of the
     * generated SQL. It consists of column name and its value pairs in the map.
     * Primary key column and read-only columns are not updatable.</p>
     *
     * @param fieldData a map of field name and its data to be set.
     * @param conditionsSQL a SQL fragment string
     * @param conditionsSQLData a data map for dynamic attributes in <tt>conditionsSQL</tt>
     * @return int number of records updated
     */
    public static int updateAll(Map fieldData, String conditionsSQL, Map conditionsSQLData) {
        throw new UnsupportedOperationException(UO);
    }


    /**
     *
     * DELETE related
     *
     */

    /**
     * Deletes the record with the given id.
     *
     * If there is no column name like "ID", an exception will be thrown.
     *
     * @param id key to the object with field name "ID"
     * @return int number of records deleted
     */
    public static int deleteById(Object id) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * Deletes the record with the given data map containing primary keys.
     *
     * If not all primary key columns have data, an exception will be thrown.
     *
     * @param inputs a map of name and value pairs
     * @return int number of records deleted
     */
    public static int deleteByPrimaryKeyMap(Map inputs) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Deletes all the records that satisfy the conditions.</p>
     *
     * <p>See top of this class for <tt>conditions</tt> examples.</p>
     *
     * <p>This method sends a single SQL DELETE statement to database. No
     * callbacks are triggered. If callbacks are necessary for handling
     * associations, use <tt>findAll</tt> method to retrieve a list of records
     * and then call each record's <tt>delete()</tt> method.</p>
     *
     * @param conditions a map of column name and value pairs
     * @return int number of records deleted
     */
    public static int deleteAll(Map conditions) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Deletes all the records that satisfy the conditions.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt> examples.</p>
     *
     * <p>This method sends a single SQL DELETE statement to database. No
     * callbacks are triggered. If callbacks are necessary for handling
     * associations, use <tt>findAll</tt> method to retrieve a list of records
     * and then call each record's <tt>delete()</tt> method.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @return int number of records deleted
     */
    public static int deleteAll(String conditionsSQL) {
        throw new UnsupportedOperationException(UO);
    }

    /**
     * <p>Deletes all the records that satisfy the conditions.</p>
     *
     * <p>See top of this class for <tt>conditionsSQL</tt>
     * and <tt>conditionsSQLData</tt> examples.</p>
     *
     * <p>This method sends a single SQL DELETE statement to database. No
     * callbacks are triggered. If callbacks are necessary for handling
     * associations, use <tt>findAll</tt> method to retrieve a list of records
     * and then call each record's <tt>delete()</tt> method.</p>
     *
     * @param conditionsSQL a SQL fragment string
     * @param conditionsSQLData a data map for dynamic attributes in <tt>conditionsSQL</tt>
     * @return int number of records deleted
     */
    public static int deleteAll(String conditionsSQL, Map conditionsSQLData) {
        throw new UnsupportedOperationException(UO);
    }


    /**
     *
     * Calculator related
     *
     */
    
    /**
     * Counts number of records.
     * 
     * @return number of records.
     */
    public static long count() {
        throw new UnsupportedOperationException(UO);
    }
    
    /**
     * Counts number of records for a field.
     * 
     * @param field name of the field
     * @return number of records.
     */
    public static long count(String field) {
        throw new UnsupportedOperationException(UO);
    }
    
    /**
     * Counts number of records for a field.
     * 
     * @param field name of the field
     * @param options options for calculation
     * @return number of records.
     */
    public static long count(String field, String options) {
        throw new UnsupportedOperationException(UO);
    }
    
    /**
     * Calculates sum of a field.
     * 
     * @param field the column name
     * @return result of calculation
     */
    public static Object sum(String field) {
        throw new UnsupportedOperationException(UO);
    }
    
    /**
     * Calculates sum of a field.
     * 
     * @param field the column name
     * @param options options of calculation
     * @return result of calculation
     */
    public static Object sum(String field, String options) {
        throw new UnsupportedOperationException(UO);
    }
    
    /**
     * Calculates average of a field.
     * 
     * @param field the column name
     * @return result of calculation
     */
    public static Object average(String field) {
        throw new UnsupportedOperationException(UO);
    }
    
    /**
     * Calculates average of a field.
     * 
     * @param field the column name
     * @param options options of calculation
     * @return result of calculation
     */
    public static Object average(String field, String options) {
        throw new UnsupportedOperationException(UO);
    }
    
    /**
     * Calculates maximum of a field.
     * 
     * @param field the column name
     * @return result of calculation
     */
    public static Object maximum(String field) {
        throw new UnsupportedOperationException(UO);
    }
    
    /**
     * Calculates maximum of a field.
     * 
     * @param field the column name
     * @param options options of calculation
     * @return result of calculation
     */
    public static Object maximum(String field, String options) {
        throw new UnsupportedOperationException(UO);
    }
    
    /**
     * Calculates minimum of a field.
     * 
     * @param field the column name
     * @return result of calculation
     */
    public static Object minium(String field) {
        throw new UnsupportedOperationException(UO);
    }
    
    /**
     * Calculates minimum of a field.
     * 
     * @param field the column name
     * @param options options of calculation
     * @return result of calculation
     */
    public static Object minium(String field, String options) {
        throw new UnsupportedOperationException(UO);
    }
    
    /**
     * Calculates by a function on a field.
     * 
     * @param function the sql function name
     * @param field the column name
     * @param options options of calculation
     * @return result of calculation
     */
    public static Object calculate(String function, String field, String options) {
        throw new UnsupportedOperationException(UO);
    }


    /**
     *
     * OTHER methods to be inserted in subclass
     *
     */


    /************************************************************************
     *
     * End of static methods declaration for subclass
     *
     ************************************************************************/

    

    /**
     * Returns a string representation of the object.
     * @return String
     */
    public String toString() {
        StringBuffer returnString = new StringBuffer();
        String separator = "\r\n";
        returnString.append("tableName = " + tableName).append(separator);
        returnString.append("existInDatabase = " + existInDatabase).append(separator);
        returnString.append("freezed = " + freezed).append(separator);
        returnString.append("dirty = " + dirty).append(separator);
        returnString.append("hasCopied = " + hasCopied).append(separator);
        returnString.append("modifiedColumns = " + modifiedColumns).append(separator);
        returnString.append("errors = " + errors).append(separator);
        returnString.append("protectedColumns = " + protectedColumns).append(separator);
        returnString.append("extraFields = " + extraFields).append(separator);
        returnString.append("extraFieldsMap = " + extraFieldsMap).append(separator);
        returnString.append("recordRelations = " + recordRelations).append(separator);
        returnString.append("rowInfo = (" + rowInfo).append(")").append(separator);
        returnString.append("rowData = (" + rowData).append(")").append(separator);
        return returnString.toString();
    }

    /**
     * Returns an XML representation of the object.
     *
     * <pre>
     * Example:
     * <post>
     *   <id>1234</id>
     *   <title>Scooter Rocks</title>
     *   <body>We love to use Scooter.</body>
     * </post>
     * </pre>
     * @return xml string
     */
    public String toXML() {
        StringBuffer xmlSB = new StringBuffer();
        String classNameInLowerCase = Util.getShortClassName(this.getClass()).toLowerCase();
        xmlSB.append("<").append(classNameInLowerCase).append(">");

        String[] colNames = rowInfo.getColumnNames();
        int colNamesLength = colNames.length;
        for (int i = 0; i < colNamesLength; i++) {
            String colName = colNames[i];
            String colNameInLowerCase = colName.toLowerCase();
            xmlSB.append("<").append(colNameInLowerCase).append(">");
            xmlSB.append(getField(colName));
            xmlSB.append("</").append(colNameInLowerCase).append(">");
        }

        synchronized(extraFields) {
            Iterator it = extraFields.iterator();
            while(it.hasNext()) {
                String extraFldName = (String)it.next();
                String extraFldNameInLowerCase = extraFldName.toLowerCase();
                xmlSB.append("<").append(extraFldNameInLowerCase).append(">");
                xmlSB.append(getField(extraFldName));
                xmlSB.append("</").append(extraFldNameInLowerCase).append(">");
            }
        }

        xmlSB.append("</").append(classNameInLowerCase).append(">");
        return xmlSB.toString();
    }

    /**
	 * Generated serialVersionUID
	 */
	private static final long serialVersionUID = 5503274145344099012L;

    //connection name
    private String connectionName = null;

    //table name
    private String tableName = null;

    //boolean to indicate whether the record is a new record
    private boolean existInDatabase = false;

    //boolean to indicate whether the record is a home record
    private boolean isHomeInstance = false;

    //boolean to indicate whether the record is freezed
    private boolean freezed = false;

    //boolean to indicate whether the record is modified and unsaved
    private boolean dirty = false;

    //boolean to indicate whether a copy has been created for the current record
    private boolean hasCopied = false;

    /**
     * list to record which columns are modified.
     * All names are in upper case.
     */
    private List modifiedColumns = new ArrayList();

    //current database record meta info
    private RowInfo rowInfo;

    //representing data in a row from database which user may modify
    private RowData rowData;

    //representing the latest data in a row from database
    private RowData latestDbRowData;

    private ValidationResults errors = new ValidationResults();

    /**
     * list of protected column names
     *
     * Data fields defined in the protectedColumns list are protected from
     * being set in massive assignments, such as setData(Map). Instead these
     * fields have to be set directly by using setData(String) or
     * setData(String, Object).
     */
    private List protectedColumns = Collections.synchronizedList(new ArrayList());

    /**
     * list of extra fields
     *
     * Extra fields are fields that are needed by the model during a transaction
     * process but not recorded in the database table.
     *
     * For example, password_confirmation.
     */
    private List extraFields = Collections.synchronizedList(new ArrayList());

    /**
     * map to store values of extra fields.
     *
     * All keys are in upper case.
     */
    private Map extraFieldsMap = Collections.synchronizedMap(new HashMap());

    /**
     * contains relation with target entities.
     *
     * Key is model name in lower case. Value is a specific RecordRelation object.
     */
    private Map recordRelations = Collections.synchronizedMap(new HashMap());

    private ModelValidators validators = null;

    protected static LogUtil log = LogUtil.getLogger(ActiveRecord.class.getName());
}
