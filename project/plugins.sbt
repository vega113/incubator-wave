// Plugins are declared here. Network fetch happens only when you run sbt.

// Fat jar bundling
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.2.0")

// Protobuf codegen (embedded protoc)
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.7")
