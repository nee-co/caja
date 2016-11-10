package utils

class Using[T <: {def close()}] private (value: T) {
  def foreach[U](f: T => U): U = try {
    f(value)
  } finally {
    value.close()
  }
}

object Using {
  def apply[T <: {def close()}](value: T) = new Using(value)
}
