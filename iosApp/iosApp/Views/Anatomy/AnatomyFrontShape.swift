import SwiftUI
import SVGPath

struct AnatomyFrontView: View {
    let selectedGroup: String?
    let onRegionTapped: (String) -> Void

    var body: some View {
        GeometryReader { geo in
            let scale = geo.size.width / MuscleRegionPaths.viewBoxWidth
            let scaledHeight = MuscleRegionPaths.viewBoxHeight * scale

            ZStack {
                // Body outline (non-interactive)
                ForEach(MuscleRegionPaths.frontOutline.indices, id: \.self) { i in
                    svgPath(MuscleRegionPaths.frontOutline[i])
                        .fill(Color(white: 0.165))
                        .scaleEffect(x: scale, y: scale, anchor: .topLeading)
                }

                // Interactive muscle regions
                ForEach(MuscleRegionPaths.frontRegions) { region in
                    let isSelected = region.groupName == selectedGroup

                    svgPath(region.pathData)
                        .fill(isSelected
                            ? Color(red: 0.4, green: 0.733, blue: 0.416).opacity(0.8)
                            : Color(white: 0.227))
                        .scaleEffect(x: scale, y: scale, anchor: .topLeading)
                        .contentShape(svgPath(region.pathData).scale(x: scale, y: scale, anchor: .topLeading))
                        .onTapGesture {
                            onRegionTapped(region.groupName)
                        }
                }
            }
            .frame(height: scaledHeight)
        }
        .aspectRatio(MuscleRegionPaths.viewBoxWidth / MuscleRegionPaths.viewBoxHeight, contentMode: .fit)
    }

    private func svgPath(_ data: String) -> Path {
        (try? Path(svgPath: data)) ?? Path()
    }
}
