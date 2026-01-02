package io.flutter.utils;

import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileUtilsTest {

  private File tempDir;
  private FileUtils fileUtils;

  @Before
  public void setUp() throws IOException {
    tempDir = FileUtil.createTempDirectory("flutter_utils_test", null);
    fileUtils = FileUtils.getInstance();
  }

  @After
  public void tearDown() {
    FileUtil.delete(tempDir);
  }

  @Test
  public void testMakeDirectory() {
    String path = new File(tempDir, "new_dir").getAbsolutePath();
    assertFalse(new File(path).exists());
    assertTrue(fileUtils.makeDirectory(path));
    assertTrue(new File(path).exists());
    assertTrue(new File(path).isDirectory());
    
    // Test existing directory
    assertTrue(fileUtils.makeDirectory(path));
  }

  @Test
  public void testFileExists() throws IOException {
    String path = new File(tempDir, "test_file.txt").getAbsolutePath();
    assertFalse(fileUtils.fileExists(path));
    
    assertTrue(new File(path).createNewFile());
    assertTrue(fileUtils.fileExists(path));
  }

  @Test
  public void testDeleteFile() throws IOException {
    String path = new File(tempDir, "delete_me.txt").getAbsolutePath();
    new File(path).createNewFile();
    assertTrue(new File(path).exists());
    
    assertTrue(fileUtils.deleteFile(path));
    assertFalse(new File(path).exists());
    
    // Test non-existent file
    assertTrue(fileUtils.deleteFile(path));
  }
}
