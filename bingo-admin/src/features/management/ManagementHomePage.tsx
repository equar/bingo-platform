import { Card, CardActionArea, CardContent, Grid, Stack, Typography } from "@mui/material";
import { Link as RouterLink } from "react-router-dom";

const sections = [
  {
    title: "Profile",
    description: "Update your password and manage your signed-in session.",
    to: "/profile",
  },
  {
    title: "Admin users",
    description: "Create and maintain admin and support accounts.",
    to: "/admin-users",
  },
  {
    title: "Agents",
    description: "Review agent status, profile data, and operational actions.",
    to: "/agents",
  },
  {
    title: "Players",
    description: "Search player accounts and inspect account details.",
    to: "/players",
  },
] as const;

export function ManagementHomePage() {
  return (
    <Stack spacing={3}>
      <div>
        <Typography variant="h4">Management</Typography>
        <Typography color="text.secondary">
          Keep admin, agent, and player account workflows separate while staying in one admin console.
        </Typography>
      </div>

      <Grid container spacing={2}>
        {sections.map((section) => (
          <Grid item xs={12} md={4} key={section.to}>
            <Card sx={{ border: "1px solid", borderColor: "divider", height: "100%" }}>
              <CardActionArea component={RouterLink} to={section.to} sx={{ height: "100%" }}>
                <CardContent>
                  <Stack spacing={1}>
                    <Typography variant="h6">{section.title}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {section.description}
                    </Typography>
                  </Stack>
                </CardContent>
              </CardActionArea>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Stack>
  );
}
