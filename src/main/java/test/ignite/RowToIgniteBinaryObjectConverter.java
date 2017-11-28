package test.ignite;

import org.apache.ignite.IgniteBinary;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectBuilder;
import org.apache.ignite.spark.JavaIgniteContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;




public class RowToIgniteBinaryObjectConverter implements Function<Row, BinaryObject> {

    private StructType schema;
    private String binaryObjectType;

    RowToIgniteBinaryObjectConverter(
        StructType schema,
        String binaryObjectType){

        this.schema = schema;
        this.binaryObjectType = binaryObjectType;
    }

    public BinaryObject call(Row r) throws Exception {

        BinaryObjectBuilder builder = Ignition.ignite().binary().builder(binaryObjectType);
        for (StructField f : schema.fields()) {

            int i = schema.fieldIndex(f.name());

            switch(f.dataType().simpleString().toLowerCase()){
                case "string": {
                    builder.setField(f.name(), r.getString(i), String.class);
                    break;
                }
                case "double": {
                    builder.setField(f.name(), r.getDouble(i), Double.class);
                    break;
                }
                case "bigint": {
                    builder.setField(f.name(), r.getLong(i), Long.class);
                    break;
                }
                case "timestamp": {
                    builder.setField(f.name(), "Must be timestamp", String.class);
                    break;
                }
            }
        }
        return builder.build();
    }
}
