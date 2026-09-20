// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "TapChoiceProtocol",
    platforms: [.macOS(.v13), .iOS(.v16)],
    products: [.library(name: "ProtocolCore", targets: ["ProtocolCore"])],
    targets: [
        .target(name: "ProtocolCore", path: "TapChoiceTerminal/Protocol"),
        .testTarget(name: "ProtocolCoreTests", dependencies: ["ProtocolCore"]),
    ]
)

