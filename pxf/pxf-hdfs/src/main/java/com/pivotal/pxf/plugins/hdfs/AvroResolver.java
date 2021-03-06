package com.pivotal.pxf.plugins.hdfs;

import com.pivotal.pxf.api.OneField;
import com.pivotal.pxf.api.OneRow;
import com.pivotal.pxf.api.ReadResolver;
import com.pivotal.pxf.api.io.DataType;
import com.pivotal.pxf.api.utilities.InputData;
import com.pivotal.pxf.api.utilities.Plugin;
import com.pivotal.pxf.plugins.hdfs.utilities.DataSchemaException;
import com.pivotal.pxf.plugins.hdfs.utilities.HdfsUtilities;
import com.pivotal.pxf.plugins.hdfs.utilities.RecordkeyAdapter;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.BytesWritable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.pivotal.pxf.api.io.DataType.*;
import static com.pivotal.pxf.plugins.hdfs.utilities.DataSchemaException.MessageFmt.SCHEMA_NOT_INDICATED;
import static com.pivotal.pxf.plugins.hdfs.utilities.DataSchemaException.MessageFmt.SCHEMA_NOT_ON_CLASSPATH;
import static com.pivotal.pxf.plugins.hdfs.utilities.HdfsUtilities.getAvroSchema;

/**
 * Class AvroResolver handles deserialization of records that were serialized
 * using the AVRO serialization framework.
 */
public class AvroResolver extends Plugin implements ReadResolver {
    private GenericRecord avroRecord = null;
    private DatumReader<GenericRecord> reader = null;
    private BinaryDecoder decoder = null;  // member kept to enable reuse, and thus avoid repeated allocation
    private List<Schema.Field> fields = null;
    private RecordkeyAdapter recordkeyAdapter = new RecordkeyAdapter();
    private static final String MAPKEY_DELIM = ":";
    private static final String RECORDKEY_DELIM = ":";
    private static final String COLLECTION_DELIM = ",";
    private String collectionDelim;
    private String mapkeyDelim;
    private String recordkeyDelim;

    /**
     * Constructs an AvroResolver. Initializes Avro data structure: the avro
     * record - fields information and the avro record reader. All Avro data is
     * build from the Avro schema, which is based on the *.avsc file that was
     * passed by the user
     *
     * @param input all input parameters coming from the client
     * @throws IOException
     */
    public AvroResolver(InputData input) throws IOException {
        super(input);

        Schema schema = isAvroFile()
                ? getAvroSchema(new Configuration(), input.getDataSource())
                : (new Schema.Parser()).parse(openExternalSchema());

        reader = new GenericDatumReader<>(schema);
        fields = schema.getFields();

        collectionDelim = input.getUserProperty("COLLECTION_DELIM") == null ? COLLECTION_DELIM
                : input.getUserProperty("COLLECTION_DELIM");
        mapkeyDelim = input.getUserProperty("MAPKEY_DELIM") == null ? MAPKEY_DELIM
                : input.getUserProperty("MAPKEY_DELIM");
        recordkeyDelim = input.getUserProperty("RECORDKEY_DELIM") == null ? RECORDKEY_DELIM
                : input.getUserProperty("RECORDKEY_DELIM");
    }

    /*
     * getFields returns a list of the fields of one record. Each record field
     * is represented by a OneField item. OneField item contains two fields:
     * an integer representing the field type and a Java Object representing
     * the field value.
     */
    @Override
    public List<OneField> getFields(OneRow row) throws Exception {
        avroRecord = makeAvroRecord(row.getData(), avroRecord);
        List<OneField> record = new LinkedList<OneField>();

        int recordkeyIndex = (inputData.getRecordkeyColumn() == null) ? -1 :
                inputData.getRecordkeyColumn().columnIndex();
        int currentIndex = 0;

        for (Schema.Field field : fields) {
            /*
             * Add the record key if exists
			 */
            if (currentIndex == recordkeyIndex) {
                currentIndex += recordkeyAdapter.appendRecordkeyField(record, inputData, row);
            }

            currentIndex += populateRecord(record, avroRecord.get(field.name()), field.schema());
        }

        return record;
    }

    /*
     * Test if the Avro records are residing inside an AVRO file.
     * If the Avro records are not residing inside an AVRO file, then
     * they may reside inside a sequence file, regular file, ...
     */
    boolean isAvroFile() {
        return inputData.getAccessor().toLowerCase().contains("avro");
    }

    /*
     * The record can arrive from one out of two different sources: a sequence
     * file or an AVRO file. If it comes from an AVRO file, then it was already
     * obtained as a GenericRecord when when it was fetched from the file with
     * the AvroRecorReader so in this case a cast is enough. On the other hand,
     * if the source is a sequence file, then the input parameter obj hides a
     * bytes [] buffer which is in fact one Avro record serialized. Here, we
     * build the Avro record from the flat buffer, using the AVRO API. Then
     * (for both cases) in the remaining functions we build a List<OneField>
     * record from the Avro record
     */
    GenericRecord makeAvroRecord(Object obj, GenericRecord reuseRecord) throws IOException {
        if (isAvroFile()) {
            return (GenericRecord) obj;
        } else {
            byte[] bytes = ((BytesWritable) obj).getBytes();
            decoder = DecoderFactory.get().binaryDecoder(bytes, decoder);
            return reader.read(reuseRecord, decoder);
        }
    }

    /*
     * For a given field in the Avro record we extract its value and insert it
     * into the output List<OneField> record. An Avro field can be a primitive
     * type or an array type.
     */
    int populateRecord(List<OneField> record, Object fieldValue, Schema fieldSchema) throws IllegalAccessException {

        Schema.Type fieldType = fieldSchema.getType();
        int ret = 0;
        Object value;

        switch (fieldType) {
            case ARRAY:
                List<OneField> listRecord = new LinkedList<>();
                ret = setArrayField(listRecord, fieldValue, fieldSchema);
                addOneFieldToRecord(record, TEXT, String.format("[%s]", HdfsUtilities.toString(listRecord, collectionDelim)));
                break;
            case MAP:
                List<OneField> mapRecord = new LinkedList<>();
                ret = setMapField(mapRecord, fieldValue, fieldSchema);
                addOneFieldToRecord(record, TEXT, String.format("{%s}", HdfsUtilities.toString(mapRecord, collectionDelim)));
                break;
            case RECORD:
                List<OneField> recRecord = new LinkedList<>();
                ret = setRecordField(recRecord, fieldValue, fieldSchema);
                addOneFieldToRecord(record, TEXT, String.format("{%s}", HdfsUtilities.toString(recRecord, collectionDelim)));
                break;
            case UNION:
                /* When an Avro field is actually a union, we resolve the type of the union
                 * element, and delegate the record update via recursion
                */
                int unionIndex = GenericData.get().resolveUnion(fieldSchema, fieldValue);
                /** Retrieve index of the non null data type from the type array if value is null */
                if (fieldValue == null) {
                    unionIndex ^= 1;
                }
                ret = populateRecord(record, fieldValue, fieldSchema.getTypes().get(unionIndex));
                break;
            case ENUM:
                value = (fieldValue != null) ? fieldValue : null;
                ret = addOneFieldToRecord(record, TEXT, value);
                break;
            case INT:
                value = (fieldValue != null) ? fieldValue : null;
                ret = addOneFieldToRecord(record, INTEGER, value);
                break;
            case DOUBLE:
                value = (fieldValue != null) ? fieldValue : null;
                ret = addOneFieldToRecord(record, FLOAT8, value);
                break;
            case STRING:
                value = (fieldValue != null) ?  String.format("%s", fieldValue) : null;
                ret = addOneFieldToRecord(record, TEXT, value);
                break;
            case FLOAT:
                value = (fieldValue != null) ? fieldValue : null;
                ret = addOneFieldToRecord(record, REAL, value);
                break;
            case LONG:
                value = (fieldValue != null) ? fieldValue : null;
                ret = addOneFieldToRecord(record, BIGINT, value);
                break;
            case BYTES:
                value = (fieldValue != null) ? fieldValue : null;
                ret = addOneFieldToRecord(record, BYTEA, value);
                break;
            case BOOLEAN:
                value = (fieldValue != null) ? fieldValue : null;
                ret = addOneFieldToRecord(record, BOOLEAN, value);
                break;
            case FIXED:
                value = (fieldValue != null) ? fieldValue : null;
                ret = addOneFieldToRecord(record, BYTEA, value);
                break;
            default:
                break;
        }
        return ret;
    }

    /*
     * When an Avro field is actually a record, we iterate through each field
     * for each entry, the field name and value are added to a local record
     * List<OneField> complexRecord with the necessary delimiter
     * we create an object of type OneField and insert it into the output List<OneField> record
     * 
     */
    int setRecordField(List<OneField> record, Object value, Schema recSchema) throws IllegalAccessException {

        GenericRecord rec = ((GenericData.Record)value);
        Schema fieldKeySchema = Schema.create(Schema.Type.STRING);
        int currentIndex = 0;
        for(Schema.Field field: recSchema.getFields()) {
            Schema fieldSchema = field.schema();
            Schema.Type fieldType = fieldSchema.getType();
            Object fieldValue = rec.get(field.name());
            List<OneField> complexRecord = new LinkedList<>();
            populateRecord(complexRecord, field.name(), fieldKeySchema);
            populateRecord(complexRecord, fieldValue, fieldSchema);
            addOneFieldToRecord(record, TEXT, HdfsUtilities.toString(complexRecord, recordkeyDelim));
            currentIndex++;
        }
        return currentIndex;
    }

    /*
     * When an Avro field is actually a map, we resolve the type of the map value
     * For each entry, the field name and value are added to a local record
     * we create an object of type OneField and insert it into the output List<OneField> record
     * Unchecked warning is suppressed to enable us to cast fieldValue to a Map.
     * (since the value schema has been identified to me of type map)
     */
    @SuppressWarnings("unchecked")
    int setMapField(List<OneField> record, Object fieldValue, Schema mapSchema) throws IllegalAccessException {
        Schema keySchema = Schema.create(Schema.Type.STRING);
        Schema valueSchema = mapSchema.getValueType();
        Map<String, ?> avroMap = ((Map<String, ?>)fieldValue);
        for (Map.Entry<String, ?> entry : avroMap.entrySet()) {
            List<OneField> complexRecord = new LinkedList<>();
            populateRecord(complexRecord, entry.getKey(), keySchema);
            populateRecord(complexRecord, entry.getValue(), valueSchema);
            addOneFieldToRecord(record, TEXT, HdfsUtilities.toString(complexRecord, mapkeyDelim));
        }
        return avroMap.size();
    }

    /*
     * When an Avro field is actually an array, we resolve the type of the array
     * element, and for each element in the Avro array, we recursively invoke
     * the population of List<OneField> record
     *
     */
    int setArrayField(List<OneField> record, Object fieldValue, Schema arraySchema) throws IllegalAccessException {
        Schema typeSchema = arraySchema.getElementType();
        GenericData.Array<?> array = (GenericData.Array<?>) fieldValue;
        int length = array.size();
        for (int i = 0; i < length; i++) {
            populateRecord(record, array.get(i), typeSchema);
        }
        return length;
    }

    /*
     * Creates the OneField object and adds it to the output List<OneField> record.
     * Strings and byte arrays are held inside special types in the Avro record so we
     * transfer them to standard types in order to enable their insertion in the
     * GPDBWritable instance
     */
    int addOneFieldToRecord(List<OneField> record, DataType gpdbWritableType, Object val) {
        OneField oneField = new OneField();
        oneField.type = gpdbWritableType.getOID();
        switch (gpdbWritableType) {
            case BYTEA:
                if(val instanceof ByteBuffer) {
                    oneField.val = ((ByteBuffer) val).array();
                } else {
                    /** Entry point when the underlying bytearray is from a Fixed data */
                    oneField.val = ((GenericData.Fixed) val).bytes();
                }
                break;
            default:
                oneField.val = val;
                break;
        }

        record.add(oneField);
        return 1;
    }

    InputStream openExternalSchema() throws IOException {

        String schemaName = inputData.getUserProperty("DATA-SCHEMA");

        /** Testing that the schema name was supplied by the user - schema is an optional properly. */
        if (schemaName == null) {
            throw new DataSchemaException(SCHEMA_NOT_INDICATED, this.getClass().getName());
        }

        /** Testing that the schema resource exists. */
        if (this.getClass().getClassLoader().getResource(schemaName) == null) {
            throw new DataSchemaException(SCHEMA_NOT_ON_CLASSPATH, schemaName);
        }
        ClassLoader loader = this.getClass().getClassLoader();
        return loader.getResourceAsStream(schemaName);
    }
}
