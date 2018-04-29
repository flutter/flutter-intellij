import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("UseJBColor")
public class DemoViewer {

  public static void main(String[] args) {
    final JFrame frame = new JFrame();
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        System.exit(0);
      }
    });
    final Container pane = frame.getContentPane();
    pane.setLayout(new BorderLayout());

    // slider to control horizontal rotation
    final JSlider headingSlider = new JSlider(-180, 180, 0);
    pane.add(headingSlider, BorderLayout.SOUTH);

    // slider to control vertical rotation
    final JSlider pitchSlider = new JSlider(SwingConstants.VERTICAL, -90, 90, 0);
    pane.add(pitchSlider, BorderLayout.EAST);

    // panel to display render results
    final JPanel renderPanel = new JPanel() {
      public void paintComponent(Graphics g) {
        final Graphics2D g2 = (Graphics2D)g;
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, getWidth(), getHeight());

        List<Triangle> tris = new ArrayList<>();
        tris.add(new Triangle(new Vertex(100, 100, 100),
                              new Vertex(-100, -100, 100),
                              new Vertex(-100, 100, -100),
                              Color.WHITE));
        tris.add(new Triangle(new Vertex(100, 100, 100),
                              new Vertex(-100, -100, 100),
                              new Vertex(100, -100, -100),
                              Color.RED));
        tris.add(new Triangle(new Vertex(-100, 100, -100),
                              new Vertex(100, -100, -100),
                              new Vertex(100, 100, 100),
                              Color.GREEN));
        tris.add(new Triangle(new Vertex(-100, 100, -100),
                              new Vertex(100, -100, -100),
                              new Vertex(-100, -100, 100),
                              Color.BLUE));

        for (int i = 0; i < 4; i++) {
          tris = inflate(tris);
        }

        final double heading = Math.toRadians(headingSlider.getValue());
        final Matrix3 headingTransform = new Matrix3(new double[]{
          Math.cos(heading), 0, -Math.sin(heading),
          0, 1, 0,
          Math.sin(heading), 0, Math.cos(heading)
        });
        final double pitch = Math.toRadians(pitchSlider.getValue());
        final Matrix3 pitchTransform = new Matrix3(new double[]{
          1, 0, 0,
          0, Math.cos(pitch), Math.sin(pitch),
          0, -Math.sin(pitch), Math.cos(pitch)
        });
        final Matrix3 transform = headingTransform.multiply(pitchTransform);

        @SuppressWarnings("UndesirableClassUsage") final BufferedImage img =
          new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);

        final double[] zBuffer = new double[img.getWidth() * img.getHeight()];
        // initialize array with extremely far away depths
        for (int q = 0; q < zBuffer.length; q++) {
          zBuffer[q] = Double.NEGATIVE_INFINITY;
        }

        for (Triangle t : tris) {
          final Vertex v1 = transform.transform(t.v1);
          v1.x += getWidth() / 2;
          v1.y += getHeight() / 2;
          final Vertex v2 = transform.transform(t.v2);
          v2.x += getWidth() / 2;
          v2.y += getHeight() / 2;
          final Vertex v3 = transform.transform(t.v3);
          v3.x += getWidth() / 2;
          v3.y += getHeight() / 2;

          final Vertex ab = new Vertex(v2.x - v1.x, v2.y - v1.y, v2.z - v1.z);
          final Vertex ac = new Vertex(v3.x - v1.x, v3.y - v1.y, v3.z - v1.z);
          final Vertex norm = new Vertex(
            ab.y * ac.z - ab.z * ac.y,
            ab.z * ac.x - ab.x * ac.z,
            ab.x * ac.y - ab.y * ac.x
          );
          final double normalLength = Math.sqrt(norm.x * norm.x + norm.y * norm.y + norm.z * norm.z);
          norm.x /= normalLength;
          norm.y /= normalLength;
          norm.z /= normalLength;

          final double angleCos = Math.abs(norm.z);

          final int minX = (int)Math.max(0, Math.ceil(Math.min(v1.x, Math.min(v2.x, v3.x))));
          final int maxX = (int)Math.min(img.getWidth() - 1, Math.floor(Math.max(v1.x, Math.max(v2.x, v3.x))));
          final int minY = (int)Math.max(0, Math.ceil(Math.min(v1.y, Math.min(v2.y, v3.y))));
          final int maxY = (int)Math.min(img.getHeight() - 1, Math.floor(Math.max(v1.y, Math.max(v2.y, v3.y))));

          final double triangleArea = (v1.y - v3.y) * (v2.x - v3.x) + (v2.y - v3.y) * (v3.x - v1.x);

          for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
              final double b1 = ((y - v3.y) * (v2.x - v3.x) + (v2.y - v3.y) * (v3.x - x)) / triangleArea;
              final double b2 = ((y - v1.y) * (v3.x - v1.x) + (v3.y - v1.y) * (v1.x - x)) / triangleArea;
              final double b3 = ((y - v2.y) * (v1.x - v2.x) + (v1.y - v2.y) * (v2.x - x)) / triangleArea;
              if (b1 >= 0 && b1 <= 1 && b2 >= 0 && b2 <= 1 && b3 >= 0 && b3 <= 1) {
                final double depth = b1 * v1.z + b2 * v2.z + b3 * v3.z;
                final int zIndex = y * img.getWidth() + x;
                if (zBuffer[zIndex] < depth) {
                  img.setRGB(x, y, getShade(t.color, angleCos).getRGB());
                  zBuffer[zIndex] = depth;
                }
              }
            }
          }
        }

        g2.drawImage(img, 0, 0, null);
      }
    };
    pane.add(renderPanel, BorderLayout.CENTER);

    headingSlider.addChangeListener(e -> renderPanel.repaint());
    pitchSlider.addChangeListener(e -> renderPanel.repaint());

    frame.setSize(400, 400);
    frame.setVisible(true);
  }

  public static Color getShade(Color color, double shade) {
    final double redLinear = Math.pow(color.getRed(), 2.4) * shade;
    final double greenLinear = Math.pow(color.getGreen(), 2.4) * shade;
    final double blueLinear = Math.pow(color.getBlue(), 2.4) * shade;

    final int red = (int)Math.pow(redLinear, 1 / 2.4);
    final int green = (int)Math.pow(greenLinear, 1 / 2.4);
    final int blue = (int)Math.pow(blueLinear, 1 / 2.4);

    return new Color(red, green, blue);
  }

  public static List<Triangle> inflate(List<Triangle> tris) {
    final List<Triangle> result = new ArrayList<>();
    for (Triangle t : tris) {
      final Vertex m1 = new Vertex((t.v1.x + t.v2.x) / 2, (t.v1.y + t.v2.y) / 2, (t.v1.z + t.v2.z) / 2);
      final Vertex m2 = new Vertex((t.v2.x + t.v3.x) / 2, (t.v2.y + t.v3.y) / 2, (t.v2.z + t.v3.z) / 2);
      final Vertex m3 = new Vertex((t.v1.x + t.v3.x) / 2, (t.v1.y + t.v3.y) / 2, (t.v1.z + t.v3.z) / 2);
      result.add(new Triangle(t.v1, m1, m3, t.color));
      result.add(new Triangle(t.v2, m1, m2, t.color));
      result.add(new Triangle(t.v3, m2, m3, t.color));
      result.add(new Triangle(m1, m2, m3, t.color));
    }
    for (Triangle t : result) {
      for (Vertex v : new Vertex[]{t.v1, t.v2, t.v3}) {
        final double l = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z) / Math.sqrt(30000);
        v.x /= l;
        v.y /= l;
        v.z /= l;
      }
    }
    return result;
  }
}

class Vertex {
  double x;
  double y;
  double z;

  Vertex(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }
}

class Triangle {
  Vertex v1;
  Vertex v2;
  Vertex v3;
  Color color;

  Triangle(Vertex v1, Vertex v2, Vertex v3, Color color) {
    this.v1 = v1;
    this.v2 = v2;
    this.v3 = v3;
    this.color = color;
  }
}

class Matrix3 {
  double[] values;

  Matrix3(double[] values) {
    this.values = values;
  }

  Matrix3 multiply(Matrix3 other) {
    final double[] result = new double[9];
    for (int row = 0; row < 3; row++) {
      for (int col = 0; col < 3; col++) {
        for (int i = 0; i < 3; i++) {
          result[row * 3 + col] +=
            this.values[row * 3 + i] * other.values[i * 3 + col];
        }
      }
    }
    return new Matrix3(result);
  }

  Vertex transform(Vertex in) {
    return new Vertex(
      in.x * values[0] + in.y * values[3] + in.z * values[6],
      in.x * values[1] + in.y * values[4] + in.z * values[7],
      in.x * values[2] + in.y * values[5] + in.z * values[8]
    );
  }
}
