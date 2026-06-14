import { createClient } from '@supabase/supabase-js'

const supabaseUrl = 'https://dcqtckwlczypsgsjksdd.supabase.co'
const supabaseKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRjcXRja3dsY3p5cHNnc2prc2RkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzUwMzA3OTUsImV4cCI6MjA1MDYwNjc5NX0.ekW2qfOAo0wzKei-ycVVmVGFugvrayvbh4v4k2YAku8'

export const supabase = createClient(supabaseUrl, supabaseKey)
