package mitll.xdata;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import org.apache.log4j.Logger;

import mitll.xdata.dataset.bitcoin.binding.BitcoinBinding;
import net.sf.javaml.core.kdtree.KDTree;

/**
 * @see mitll.xdata.dataset.bitcoin.binding.BitcoinBinding#getSimilarity(String, String)
 */
public class NodeSimilaritySearch {
  private static Logger logger = Logger.getLogger(NodeSimilaritySearch.class);

  private List<String> ids;
  private Map<String, Integer> idToRow;
  private float[][] features;
  private int numRows;
  private int numFeatures;
  private KDTree kdtree;

  /**
   * Just for testing
   * @param idFile
   * @param featureFile
   * @throws Exception
   */
  private NodeSimilaritySearch(String idFile, String featureFile) throws Exception {
    load(idFile, featureFile);
  }

  /**
   * @param idFile
   * @param featureFile
   * @throws Exception
   * @see mitll.xdata.dataset.bitcoin.binding.BitcoinBinding#BitcoinBinding(mitll.xdata.db.DBConnection, boolean)
   * @see mitll.xdata.dataset.kiva.binding.KivaBinding#KivaBinding(mitll.xdata.db.DBConnection)
   */
  public NodeSimilaritySearch(InputStream idFile, InputStream featureFile) throws Exception {
    load(idFile, featureFile);
  }

  private void load(String idFile, String featureFile) throws Exception {
    loadIDs(idFile);
    loadFeatures(featureFile);
    index();
  }

  private void load(InputStream idFile, InputStream featureFile) throws Exception {
    loadIDs(idFile);
    loadFeatures(featureFile);
    index();
  }

  private void loadIDs(String filename) throws Exception {
    InputStream in = new FileInputStream(filename);
    loadIDs(in);
  }

  private void loadIDs(InputStream in) throws IOException {
    BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
    ids = new ArrayList<String>();
    idToRow = new HashMap<String, Integer>();
    // skip header
    String line = br.readLine();
    while ((line = br.readLine()) != null) {
      String id = line.trim();
      if (id.length() > 0) {
        idToRow.put(id, ids.size());
        ids.add(id);
      }
      if (ids.size() % 200000 == 0) {
        logger.debug("loading ids: ids.size() = " + ids.size());
      }
    }
    br.close();
    numRows = ids.size();
  }

  private void loadFeatures(String filename) throws Exception {
    InputStream in = new FileInputStream(filename);
    loadFeatures(in);
  }

  /**
   * Read tsv features from stream
   * TODO : try not to go back and forth between float and double
   * @param in
   * @throws IOException
   */
  private void loadFeatures(InputStream in) throws IOException {
    BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
    String line = null;

    // determine number of features from tsv header
    line = br.readLine();
    List<String> fields = split(line, "\t");
    // skip first column (which has an id meant for browsing through data)
    numFeatures = fields.size() - 1;

    // initialize feature array
    features = new float[ids.size()][numFeatures];

    int row = 0;
    while ((line = br.readLine()) != null) {
      if (row >= ids.size()) {
        logger.warn("more feature rows than IDs? row = " + row + ", ids.size() = " + ids.size());
        break;
      }
      fields = split(line, "\t");
      // skip first field (which has an id meant for browsing through data)
      for (int i = 0; i < numFeatures; i++) {
        features[row][i] = (float) Double.parseDouble(fields.get(i + 1));
      }
      row++;

      if (row % 200000 == 0) {
        logger.debug("loading features: " + (100.0 * row / ids.size()) + "% done");
      }
    }

    logger.debug("loading features: " + (100.0 * row / ids.size()) + "% done");

    br.close();
  }

  /**
   * Split string into fields.
   * <p/>
   * Note: Assumes at least one field.
   */
  private List<String> split(String s, String separator) {
    List<String> fields = new ArrayList<String>();
    int i = 0;
    // add fields up to last separator
    while (i < s.length()) {
      int index = s.indexOf(separator, i);
      if (index < 0) {
        break;
      }
      fields.add(s.substring(i, index));
      i = index + 1;
    }
    // add field after last separator
    fields.add(s.substring(i, s.length()));
    return fields;
  }

  /**
   * Build the KDTree
   * @see net.sf.javaml.core.kdtree.KDTree
   * @see #load(java.io.InputStream, java.io.InputStream)
   */
  private void index() {
    kdtree = new KDTree(numFeatures);
    // note: this is just temporary to make sure there are no duplicate keys (feature vectors)
    Set<double[]> unique = new HashSet<double[]>();
    for (int i = 0; i < numRows; i++) {
      String id = ids.get(i);
      double[] key = floatToDouble(features[i]);
      if (unique.contains(key)) {
        logger.warn("key not unique for id = " + id);
        continue;
      }
      kdtree.insert(key, id);
      unique.add(key);
    }
  }

  private double[] floatToDouble(float[] f) {
    double[] d = new double[f.length];
    for (int i = 0; i < f.length; i++) {
      d[i] = f[i];
    }
    return d;
  }

  /**
   * @param id
   * @param k
   * @return
   * @see BitcoinBinding#getNearestNeighbors(String, int, boolean)
   */
  public List<String> neighbors(String id, int k) {
    List<String> results = new ArrayList<String>();
    if (!idToRow.containsKey(id)) {
      return results;
    }
    k = Math.min(k, ids.size());
    int row = idToRow.get(id);
    double[] key = floatToDouble(features[row]);
    Object[] objects = kdtree.nearest(key, k);
    for (Object object : objects) {
      results.add((String) object);
    }
    return results;
  }

  /**
   * @param id0
   * @param id1
   * @return
   * @see BitcoinBinding#getSimilarity(String, String)
   * @see mitll.xdata.dataset.kiva.binding.KivaBinding#getSimilarity(String, String)
   */
  public double similarity(String id0, String id1) {
    return 1.0 / (1.0 + distance(id0, id1));
  }

  /**
   * @param id0
   * @param id1
   * @return
   * @see #similarity(String, String)
   */
  private double distance(String id0, String id1) {
    if (!idToRow.containsKey(id0) || !idToRow.containsKey(id1)) {
      return Double.MAX_VALUE;
    }
    double d = 0.0;
    int row0 = idToRow.get(id0);
    int row1 = idToRow.get(id1);
    for (int i = 0; i < numFeatures; i++) {
      double diff = features[row0][i] - features[row1][i];
      d += diff * diff;
    }
    d = Math.sqrt(d);
    return d;
  }

  public static void main(String[] args) throws Exception {
    // String idFile = "c:/temp/kiva/kiva_similarity/partner_ids.tsv";
    // String featureFile = "c:/temp/kiva/kiva_similarity/partner_features_standardized.tsv";
    // String id = "p137";

    String idFile = "c:/temp/kiva/kiva_similarity/kiva_feats_tsv/lender_ids.tsv";
    String featureFile = "c:/temp/kiva/kiva_similarity/kiva_feats_tsv/lender_features_standardized.tsv";
    String id = "l0376099";

    System.out.println("building search index...");
    NodeSimilaritySearch search = new NodeSimilaritySearch(idFile, featureFile);
    search.load(idFile, featureFile);
    System.out.println("searching...");
    List<String> results = search.neighbors(id, 20);
    System.out.println(results);

    System.out.println("done");
  }
}
