import Shared
import UIKit

final class MapHostViewController: UIViewController {
    let renderer: MapLibreRenderer

    init(eventSink: IosMapEventSink) {
        renderer = MapLibreRenderer(eventSink: eventSink)
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    override func loadView() {
        view = renderer.mapView
    }

    func update(state: IosMapRenderState) {
        renderer.update(state: state)
    }

    func releaseResources() {
        renderer.releaseResources()
    }
}

private final class MapHostContainerView: UIView {
    private let hostController: MapHostViewController

    init(eventSink: IosMapEventSink) {
        hostController = MapHostViewController(eventSink: eventSink)
        super.init(frame: .zero)
        let mapView = hostController.view!
        addSubview(mapView)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        hostController.view.frame = bounds
    }

    func update(state: IosMapRenderState) {
        hostController.update(state: state)
    }

    func releaseResources() {
        hostController.releaseResources()
    }
}

final class IosMapFactory: NSObject, IosNativeMapFactory {
    func createMapView(eventSink_: IosMapEventSink) -> UIView {
        MapHostContainerView(eventSink: eventSink_)
    }

    func updateMapView(mapView: UIView, state_: IosMapRenderState) {
        (mapView as? MapHostContainerView)?.update(state: state_)
    }

    func releaseMapView(mapView: UIView) {
        (mapView as? MapHostContainerView)?.releaseResources()
    }
}
