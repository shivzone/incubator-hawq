package com.pivotal.hawq.mapreduce.ut;

import com.google.common.collect.Lists;
import com.pivotal.hawq.mapreduce.HAWQTable;
import com.pivotal.hawq.mapreduce.SimpleTableLocalTester;
import com.pivotal.hawq.mapreduce.metadata.HAWQTableFormat;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test data type support for Parquet table.
 */
public class HAWQInputFormatUnitTest_Parquet_Types extends SimpleTableLocalTester {

	private void doSingleColumnParquetTest(String columnType) throws Exception {
		String tableName = "test_parquet_types_" + columnType;
		tableName = tableName.replace("(", "")
							 .replace(")", "")
							 .replace("[]", "_array");

		HAWQTable table = new HAWQTable.Builder(tableName, Lists.newArrayList(columnType))
				.storage(HAWQTableFormat.Parquet)
				.build();

		testSimpleTable(table);
	}

	@Test
	public void testBool() throws Exception {
		doSingleColumnParquetTest("bool");
	}

	@Test
	public void testBit() throws Exception {
		doSingleColumnParquetTest("bit");
	}

	@Test
	public void testBitN() throws Exception {
		doSingleColumnParquetTest("bit(5)");
	}

	@Test
	public void testVarbit() throws Exception {
		doSingleColumnParquetTest("varbit");
	}

	@Test
	public void testByteArray() throws Exception {
		doSingleColumnParquetTest("bytea");
	}

	@Test
	public void testInt2() throws Exception {
		doSingleColumnParquetTest("int2");
	}

	@Test
	public void testInt4() throws Exception {
		doSingleColumnParquetTest("int4");
	}

	@Test
	public void testInt8() throws Exception {
		doSingleColumnParquetTest("int8");
	}

	@Test
	public void testFloat4() throws Exception {
		doSingleColumnParquetTest("float4");
	}

	@Test
	public void testFloat8() throws Exception {
		doSingleColumnParquetTest("float8");
	}

	@Test
	public void testNumeric() throws Exception {
		doSingleColumnParquetTest("numeric");
	}

	@Test
	public void testCharN() throws Exception {
		doSingleColumnParquetTest("char(10)");
	}

	@Test
	public void testVarcharN() throws Exception {
		doSingleColumnParquetTest("varchar(10)");
	}

	@Test
	public void testText() throws Exception {
		doSingleColumnParquetTest("text");
	}

	@Test
	public void testDate() throws Exception {
		doSingleColumnParquetTest("date");
	}

	@Test
	public void testTime() throws Exception {
		doSingleColumnParquetTest("time");
	}

	@Ignore("we cannot use a static answer file for timetz as we did in all " +
			"UTs, because its value depends on the machine running the tests." +
			"Nonetheless, timetz will still be tested in FT")
	@Test
	public void testTimetz() throws Exception {
		doSingleColumnParquetTest("timetz");
	}

	@Test
	public void testTimestamp() throws Exception {
		doSingleColumnParquetTest("timestamp");
	}

	@Ignore("the same reason as timetz")
	@Test
	public void testTimestamptz() throws Exception {
		doSingleColumnParquetTest("timestamptz");
	}

	@Test
	public void testInterval() throws Exception {
		doSingleColumnParquetTest("interval");
	}

	@Test
	public void testPoint() throws Exception {
		doSingleColumnParquetTest("point");
	}

	@Test
	public void testLseg() throws Exception {
		doSingleColumnParquetTest("lseg");
	}

	@Test
	public void testBox() throws Exception {
		doSingleColumnParquetTest("box");
	}

	@Test
	public void testCircle() throws Exception {
		doSingleColumnParquetTest("circle");
	}

	@Test
	public void testPath() throws Exception {
		doSingleColumnParquetTest("path");
	}

	@Test
	public void testPolygon() throws Exception {
		doSingleColumnParquetTest("polygon");
	}

	@Test
	public void testMacaddr() throws Exception {
		doSingleColumnParquetTest("macaddr");
	}

	@Test
	public void testInet() throws Exception {
		doSingleColumnParquetTest("inet");
	}

	@Test
	public void testCidr() throws Exception {
		doSingleColumnParquetTest("cidr");
	}

	@Test
	public void testXml() throws Exception {
		doSingleColumnParquetTest("xml");
	}
}
