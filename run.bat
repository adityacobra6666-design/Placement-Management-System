@echo off
echo ==============================================================
echo  Starting Student Placement Management System Backend
echo ==============================================================

java -cp "bin;lib/ojdbc6.jar" com.placement.server.PlacementServer
