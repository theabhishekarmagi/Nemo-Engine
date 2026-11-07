# Nearby searches and skills

## Nearby searches

For a request such as “find the best momos near me”, the assistant can use `current_location` to read your phone location without requiring phone-control debugging.

The phone’s Location switch and the app’s location permission are separate. On first use, the app explains how location will be used, then Android asks for permission. Choose “While using the app” for future nearby requests. Approximate location is supported; precise location can improve nearby distances.

Location is read on demand while the app is visible. There is no background location permission or continuous tracking. Remote, unattended and child runs are not offered the location tool. Fixes older than two minutes and invalid coordinates are rejected. If permission is denied, Location is off, or a fix is unavailable, the assistant explains the problem rather than inventing a location.

Coordinates enter the current assistant task and may be sent to the configured AI provider and used in location-dependent web searches. Hidden browser pages do not automatically receive geolocation permission; searches can use the native location result instead. The model is instructed not to save these coordinates in memory or skills.

## Skills

Skills are reusable step-by-step instructions the assistant reads when doing a matching task. Built-in guides cover common apps and websites; personal skills capture workflows you want to reuse.

With “Learn from past tasks (skills and app notes)” enabled in Settings > Memory & skills, suitable successful multi-step tasks can produce a draft. Open “To review” to inspect, edit, accept or reject it. Drafts are not used until accepted. Accepted skills start as trials, successful use can activate them, and repeated failures can suspend them. You can view, disable, restore or delete skills in the Skills tab.

You can also explicitly ask the assistant to save a workflow as a skill. A useful food-search skill would say: read the current location, search nearby places, compare ratings and distance, and present options. It should use a fresh location each time rather than storing a fixed address.

Skills guide the model’s work; normal app permissions and any required model connection still apply.
