# Xylune 0.23.13

## Back transitions now finish

Back navigation no longer performs a short preview movement and then removes the current page. Ordinary Back and Android predictive Back now move both opaque pages edge-to-edge across the full viewport and settle before the source composition is retired.

Returning from the setup detours is also handled by the same navigation host. Providers and Tool workspace slide directly back to the preserved setup page; Xylune changes the setup state only after that animation has completed, preventing the whole host from being replaced mid-transition.
