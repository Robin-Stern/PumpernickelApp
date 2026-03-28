import Foundation

// Stub -- will be fully implemented in Task 2

struct MuscleRegionPath {
    let regionId: String
    let groupName: String
    let pathData: String
    let view: AnatomySide
}

enum AnatomySide {
    case front, back
}

struct MuscleRegionPaths {
    static let viewBoxWidth: CGFloat = 676.49
    static let viewBoxHeight: CGFloat = 1203.49

    static let frontRegions: [MuscleRegionPath] = []
    static let backRegions: [MuscleRegionPath] = []
    static let frontOutline: [String] = []
    static let backOutline: [String] = []
}
