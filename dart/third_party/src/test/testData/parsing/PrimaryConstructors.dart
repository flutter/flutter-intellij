// Basic primary constructor
class Point(var int x, var int y);

// With body part and initializer
class DeltaPoint(final int x, int delta) {
  final int y;
  this : y = x + delta;
}

// Constant primary constructor
class const ConstPoint(final int x, final int y);

// Extension type
extension type const E.name(int x);

// Optional parameters
class OptionalPoint(var int x, [var int y = 0]);

// Named parameters
class NamedPoint(var int x, {required var int y});

// Empty body with semicolon
class Empty;

class F {
  new(int x);
  new f(int x);
  factory ff() => F.f(0);
}

class G.g(var int x) {
  factory () => G.g(2);
}

// Explicit default primary constructor
class H.new(int x);

// Enum with primary constructor
enum MyEnum(final int value) {
  one(1),
  two(2);
}

// Metadata on parameters
const meta = 42;
class C9(@meta var int x, @meta int y);

// new shortcut with const and initializers
class C10 {
  final int v;
  @meta const new(int v) : v = v;
}

class E {
  E? parent;
}

class D extends E {
  E expression;
  D.d(this.expression) {
    expression.parent = this;
  }

  new(this.expression) {
    expression.parent = this;
  }

  void foo() {
    expression.parent = this;
  }
}

// EOF

