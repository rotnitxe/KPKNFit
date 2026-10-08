// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "KPKNContracts", platforms: [.iOS(.v16)],
    products: [.library(name: "KPKNContracts", targets: ["KPKNContracts"])],
    targets: [.target(name: "KPKNContracts"), .testTarget(name: "KPKNContractsTests", dependencies: ["KPKNContracts"])])
