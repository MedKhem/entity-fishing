package com.scienceminer.nerd.kb.db;

import org.apache.hadoop.record.CsvRecordInput;

import java.math.BigInteger;
import java.io.*;

import com.scienceminer.nerd.utilities.*;

import org.fusesource.lmdbjni.*;
import static org.fusesource.lmdbjni.Constants.*;

/**
 * A {@link KBDatabase} for associating Integer keys with a Long value object.
 *
 */
public abstract class IntIntDatabase extends KBDatabase<Integer, Integer> {

	/**
	 * Creates or connects to a database, whose name will match the given {@link KBDatabe.DatabaseType}
	 * 
	 * @param env the KBEnvironment surrounding this database
	 * @param type the type of database
	 */
	public IntIntDatabase(KBEnvironment envi, DatabaseType type) {
		super(envi, type);
	}
	
	/**
	 * Creates or connects to a database with the given name.
	 * 
	 * @param env the KBEnvironment surrounding this database
	 * @param type the type of database
	 * @param name the name of the database 
	 */
	public IntIntDatabase(KBEnvironment envi, DatabaseType type, String name) {
		super(envi, type, name);
	}
		
	// using standard LMDB copy mode
	@Override
	public Integer retrieve(Integer key) {
		byte[] cachedData = null;
		Integer record = null;
		try (Transaction tx = environment.createReadTransaction()) {
			//cachedData = db.get(tx, BigInteger.valueOf(key).toByteArray());
			cachedData = db.get(tx, KBEnvironment.serialize(key));
			if (cachedData != null) {
				record = (Integer)KBEnvironment.deserialize(cachedData);
				//record = new BigInteger(cachedData).intValue();
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		return record;
	}

	// using LMDB zero copy mode
	//@Override
	public Integer retrieve2(Integer key) {
		byte[] cachedData = null;
		Integer record = null;
		try (Transaction tx = environment.createReadTransaction();
			BufferCursor cursor = db.bufferCursor(tx)) {
			cursor.keyWriteBytes(KBEnvironment.serialize(key));
			if (cursor.seekKey()) {
				record = (Integer)KBEnvironment.deserialize(cursor.valBytes());
				//record = new BigInteger(cursor.valBytes()).intValue();
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		return record;
	}
	
	protected void add(KBEntry<Integer,Integer> entry) {
		try (Transaction tx = environment.createWriteTransaction()) {
			//db.put(tx, BigInteger.valueOf(entry.getKey()).toByteArray(), BigInteger.valueOf(entry.getValue()).toByteArray());
			db.put(tx, KBEnvironment.serialize(entry.getKey()), KBEnvironment.serialize(entry.getValue()));
			tx.commit();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Builds the persistent database from a file.
	 * 
	 * @param dataFile the file (here a CSV file) containing data to be loaded
	 * @param overwrite true if the existing database should be overwritten, otherwise false
	 * @throws IOException if there is a problem reading or deserialising the given data file.
	 */
	public void loadFromFile(File dataFile, boolean overwrite) throws IOException  {
		if (isLoaded && !overwrite)
			return;
		System.out.println("Loading " + name + " database");

		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(dataFile), "UTF-8"));
		long bytesRead = 0;

		String line = null;
		int nbToAdd = 0;
		Transaction tx = environment.createWriteTransaction();
		while ((line=input.readLine()) != null) {
			if (nbToAdd == 10000) {
				tx.commit();
				tx.close();
				nbToAdd = 0;
				tx = environment.createWriteTransaction();
			}

			bytesRead = bytesRead + line.length() + 1;

			CsvRecordInput cri = new CsvRecordInput(new ByteArrayInputStream((line + "\n").getBytes("UTF-8")));
			KBEntry<Integer,Integer> entry = deserialiseCsvRecord(cri);
			if (entry != null) {
				try {
					//db.put(tx, BigInteger.valueOf(entry.getKey()).toByteArray(), BigInteger.valueOf(entry.getValue()).toByteArray());
					db.put(tx, KBEnvironment.serialize(entry.getKey()), KBEnvironment.serialize(entry.getValue()));
					nbToAdd++;
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
		}
		tx.commit();
		tx.close();
		input.close();
		isLoaded = true;
	}

}
