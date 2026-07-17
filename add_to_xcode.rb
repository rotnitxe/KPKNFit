require 'rubygems'
require 'xcodeproj'

project_path = 'ios-native/KPKNFit/KPKNFit.xcodeproj'
project = Xcodeproj::Project.open(project_path)
target = project.targets.find { |t| t.name == 'KPKNFit' }

def add_file_to_project(project, target, file_name, group_path)
  group_names = group_path.split('/')
  current_group = project.main_group
  
  group_names.each do |name|
    next_group = current_group.groups.find { |g| g.display_name == name || g.path == name }
    if next_group.nil?
      next_group = current_group.new_group(name, name)
    end
    current_group = next_group
  end
  
  old_ref = current_group.files.find { |f| f.display_name == file_name || f.path == file_name || f.name == file_name }
  if old_ref
    target.source_build_phase.remove_file_reference(old_ref)
    old_ref.remove_from_project
  end
  
  file_ref = current_group.new_file(file_name)
  file_ref.set_path("#{group_path}/#{file_name}")
  file_ref.source_tree = 'SOURCE_ROOT'
  
  target.source_build_phase.add_file_reference(file_ref)
  puts "Added #{file_name} correctly with absolute project path"
end

add_file_to_project(project, target, 'AppColors.swift', 'KPKNFit/Presentation/Theme')
add_file_to_project(project, target, 'LiquidGlassModifier.swift', 'KPKNFit/Presentation/Theme')
add_file_to_project(project, target, 'NeonOrbView.swift', 'KPKNFit/Presentation/Theme')
add_file_to_project(project, target, 'MyRingsView.swift', 'KPKNFit/Presentation/Components')

project.save
puts "Project saved successfully!"
